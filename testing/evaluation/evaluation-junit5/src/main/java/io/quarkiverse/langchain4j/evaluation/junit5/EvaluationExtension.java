package io.quarkiverse.langchain4j.evaluation.junit5;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.ReportFormatterRegistry;
import io.quarkiverse.langchain4j.testing.evaluation.SampleLoaderResolver;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;

public class EvaluationExtension implements BeforeEachCallback, AfterEachCallback, AfterAllCallback, ParameterResolver {
    private static final Logger LOG = Logger.getLogger(EvaluationExtension.class);
    private static final String TEST_INSTANCE_KEY = "testInstance";
    private static final String REPORTS_KEY = "reports";
    private final List<Scorer> scorers = new CopyOnWriteArrayList<>();

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        // Store test instance for report generation in afterAll using root context store
        Object testInstance = extensionContext.getRequiredTestInstance();
        extensionContext.getRoot()
                .getStore(ExtensionContext.Namespace.create(EvaluationExtension.class, extensionContext.getRequiredTestClass()))
                .put(TEST_INSTANCE_KEY, testInstance);

        Optional<Class<?>> maybeClass = extensionContext.getTestClass();
        if (maybeClass.isPresent()) {
            List<Field> fields = ReflectionSupport.findFields(maybeClass.get(),
                    field -> field.getType().isAssignableFrom(Scorer.class), HierarchyTraversalMode.TOP_DOWN);
            for (Field field : fields) {
                Scorer sc;
                if (field.isAnnotationPresent(ScorerConfiguration.class)) {
                    ScorerConfiguration annotation = field.getAnnotation(ScorerConfiguration.class);
                    sc = new Scorer(annotation.concurrency());
                } else {
                    sc = new Scorer();
                }
                scorers.add(sc);
                inject(sc, testInstance, field);
            }
        }
    }

    private void inject(Scorer sc, Object instance, Field field) {
        try {
            field.setAccessible(true);
            field.set(instance, sc);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        // Capture reports from @ReportConfiguration fields and store them for afterAll
        // This is needed because in Quarkus tests, the test instance might become inaccessible later
        Optional<Class<?>> maybeClass = extensionContext.getTestClass();
        if (maybeClass.isPresent()) {
            Class<?> testClass = maybeClass.get();
            Object testInstance = extensionContext.getRequiredTestInstance();

            List<Field> reportFields = ReflectionSupport.findFields(
                    testClass,
                    field -> field.getType().isAssignableFrom(EvaluationReport.class)
                            && field.isAnnotationPresent(ReportConfiguration.class),
                    HierarchyTraversalMode.TOP_DOWN);

            if (!reportFields.isEmpty()) {
                // Store reports in the root context store so they're accessible in afterAll
                ExtensionContext.Store store = extensionContext.getRoot()
                        .getStore(ExtensionContext.Namespace.create(EvaluationExtension.class, testClass));

                @SuppressWarnings("unchecked")
                Map<String, EvaluationReport<?>> reports = (Map<String, EvaluationReport<?>>) store.get(REPORTS_KEY);
                if (reports == null) {
                    reports = new HashMap<>();
                }

                for (Field field : reportFields) {
                    try {
                        field.setAccessible(true);
                        EvaluationReport<?> report = (EvaluationReport<?>) field.get(testInstance);
                        if (report != null) {
                            // Store the report with the field name as key
                            reports.put(field.getName(), report);
                            // Also store the configuration for later use
                            store.put(field.getName() + "_config", field.getAnnotation(ReportConfiguration.class));
                        }
                    } catch (IllegalAccessException e) {
                        LOG.warnf(e, "Failed to access report field %s.%s",
                                testClass.getSimpleName(), field.getName());
                    }
                }

                store.put(REPORTS_KEY, reports);
            }
        }

        for (Scorer scorer : scorers) {
            scorer.close();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        // Generate reports for all @ReportConfiguration annotated fields
        Optional<Class<?>> maybeClass = extensionContext.getTestClass();
        if (maybeClass.isEmpty()) {
            return;
        }

        Class<?> testClass = maybeClass.get();
        ExtensionContext.Store store = extensionContext.getRoot()
                .getStore(ExtensionContext.Namespace.create(EvaluationExtension.class, testClass));

        // Retrieve reports from the store (captured in afterEach)
        @SuppressWarnings("unchecked")
        Map<String, EvaluationReport<?>> reports = (Map<String, EvaluationReport<?>>) store.get(REPORTS_KEY);

        if (reports == null || reports.isEmpty()) {
            // No reports to generate
            return;
        }

        // Generate reports for each field that had a non-null report
        for (Map.Entry<String, EvaluationReport<?>> entry : reports.entrySet()) {
            String fieldName = entry.getKey();
            EvaluationReport<?> report = entry.getValue();

            // Retrieve the configuration for this field
            ReportConfiguration config = (ReportConfiguration) store.get(fieldName + "_config");
            if (config == null) {
                LOG.warnf("No configuration found for report field %s", fieldName);
                continue;
            }

            generateReports(report, config, testClass.getSimpleName());
        }
    }

    @SuppressWarnings("rawtypes")
    private void generateReports(EvaluationReport report, ReportConfiguration config, String defaultBaseName) {
        try {
            Path outputDir = Paths.get(config.outputDir());
            Files.createDirectories(outputDir);

            String baseName = config.fileName().isEmpty() ? defaultBaseName : config.fileName();

            // Determine which formats to generate
            String[] formats = config.formats();
            if (formats.length == 0) {
                // Generate all supported formats
                Set<String> supportedFormats = ReportFormatterRegistry.supportedFormats();
                formats = supportedFormats.toArray(new String[0]);
            }

            // Build configuration map
            Map<String, Object> formatterConfig = new HashMap<>();
            formatterConfig.put("includeDetails", config.includeDetails());
            formatterConfig.put("pretty", config.pretty());

            // Generate reports for each format
            for (String format : formats) {
                try {
                    Path outputFile = outputDir.resolve(baseName +
                            ReportFormatterRegistry.get(format).fileExtension());
                    report.saveAs(outputFile, format, formatterConfig);
                    LOG.infof("Generated %s report: %s", format, outputFile);
                } catch (IllegalArgumentException e) {
                    LOG.errorf("Unknown report format: %s. Available formats: %s",
                            format, ReportFormatterRegistry.supportedFormats());
                } catch (IOException e) {
                    LOG.errorf(e, "Failed to generate %s report", format);
                }
            }
        } catch (IOException e) {
            LOG.errorf(e, "Failed to create output directory: %s", config.outputDir());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return (parameterContext.findAnnotation(SampleLocation.class).isPresent()
                && parameterContext.getParameter().getType().isAssignableFrom(Samples.class))
                || (parameterContext.findAnnotation(SampleSources.class).isPresent()
                        && parameterContext.getParameter().getType().isAssignableFrom(Samples.class))
                || parameterContext.getParameter().getType().isAssignableFrom(Scorer.class)
                || parameterContext.findAnnotation(EvaluationFunction.class).isPresent();
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        if (parameterContext.getParameter().getType().isAssignableFrom(Scorer.class)) {
            if (parameterContext.getParameter().isAnnotationPresent(ScorerConfiguration.class)) {
                ScorerConfiguration annotation = parameterContext.getParameter().getAnnotation(ScorerConfiguration.class);
                return new Scorer(annotation.concurrency());
            } else {
                return new Scorer();
            }
        } else if (parameterContext.findAnnotation(EvaluationFunction.class).isPresent()) {
            // Resolve evaluation function by name
            EvaluationFunction annotation = parameterContext.findAnnotation(EvaluationFunction.class).get();
            String functionName = annotation.value();

            if (functionName == null || functionName.isEmpty()) {
                throw new ParameterResolutionException(
                        "@EvaluationFunction on parameter must specify a function name");
            }

            return resolveEvaluationFunction(functionName, extensionContext);
        } else if (parameterContext.findAnnotation(SampleSources.class).isPresent()) {
            // Multiple sample sources - load and combine them
            SampleSources annotation = parameterContext.findAnnotation(SampleSources.class).get();
            return loadAndCombineSamples(annotation.value());
        } else {
            // Single sample source - use SampleLoaderResolver for hybrid CDI/ServiceLoader discovery
            String path = parameterContext.findAnnotation(SampleLocation.class).orElseThrow().value();
            return SampleLoaderResolver.load(path, Object.class);
        }
    }

    /**
     * Resolve an evaluation function by name from the test class.
     */
    private Object resolveEvaluationFunction(String functionName, ExtensionContext extensionContext) {
        Optional<Class<?>> maybeClass = extensionContext.getTestClass();
        if (maybeClass.isEmpty()) {
            throw new ParameterResolutionException("No test class found");
        }

        Class<?> testClass = maybeClass.get();
        Object testInstance = extensionContext.getRequiredTestInstance();

        // Find method annotated with @EvaluationFunction matching the name
        List<java.lang.reflect.Method> methods = ReflectionSupport.findMethods(
                testClass,
                method -> {
                    if (!method.isAnnotationPresent(EvaluationFunction.class)) {
                        return false;
                    }
                    EvaluationFunction annotation = method.getAnnotation(EvaluationFunction.class);
                    String name = annotation.value().isEmpty() ? method.getName() : annotation.value();
                    return name.equals(functionName);
                },
                HierarchyTraversalMode.TOP_DOWN);

        if (methods.isEmpty()) {
            throw new ParameterResolutionException(
                    String.format("No @EvaluationFunction method found with name '%s'", functionName));
        }

        if (methods.size() > 1) {
            throw new ParameterResolutionException(
                    String.format("Multiple @EvaluationFunction methods found with name '%s'", functionName));
        }

        java.lang.reflect.Method method = methods.get(0);
        try {
            method.setAccessible(true);
            return method.invoke(testInstance);
        } catch (Exception e) {
            throw new ParameterResolutionException(
                    String.format("Failed to invoke @EvaluationFunction method '%s'", functionName), e);
        }
    }

    /**
     * Load samples from multiple sources and combine them into a single Samples instance.
     */
    private Samples<Object> loadAndCombineSamples(SampleLocation[] locations) {
        if (locations == null || locations.length == 0) {
            throw new ParameterResolutionException("@SampleSources must specify at least one @SampleLocation");
        }

        // Load samples from each location
        List<io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample<Object>> allSamples = new java.util.ArrayList<>();

        for (SampleLocation location : locations) {
            Samples<Object> samples = SampleLoaderResolver.load(location.value(), Object.class);
            allSamples.addAll(samples);
        }

        if (allSamples.isEmpty()) {
            throw new ParameterResolutionException(
                    "No samples found from any of the specified sources");
        }

        return new Samples<>(allSamples);
    }

}

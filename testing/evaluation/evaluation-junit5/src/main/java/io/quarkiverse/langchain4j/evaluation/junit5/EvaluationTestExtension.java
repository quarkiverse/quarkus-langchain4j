package io.quarkiverse.langchain4j.evaluation.junit5;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.Parameters;
import io.quarkiverse.langchain4j.testing.evaluation.ReportFormatterRegistry;
import io.quarkiverse.langchain4j.testing.evaluation.SampleLoaderResolver;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;

/**
 * JUnit 5 extension for {@link EvaluationTest} annotation.
 * <p>
 * This extension provides test template invocation contexts for @EvaluationTest methods.
 * </p>
 */
public class EvaluationTestExtension implements TestTemplateInvocationContextProvider {

    private static final Logger LOG = Logger.getLogger(EvaluationTestExtension.class);

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> method.isAnnotationPresent(EvaluationTest.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        // Return a single invocation context that will execute the evaluation
        return Stream.of(new EvaluationTestInvocationContext(context));
    }

    /**
     * Custom invocation context that executes the evaluation and validates the score.
     */
    private static class EvaluationTestInvocationContext implements TestTemplateInvocationContext {

        private final ExtensionContext extensionContext;

        EvaluationTestInvocationContext(ExtensionContext extensionContext) {
            this.extensionContext = extensionContext;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            EvaluationTest annotation = extensionContext.getRequiredTestMethod()
                    .getAnnotation(EvaluationTest.class);
            return String.format("Evaluation (min score: %.2f%%)", annotation.minScore());
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            // Add an invocation interceptor to execute the evaluation
            return Collections.singletonList(new EvaluationInvocationInterceptor());
        }
    }

    /**
     * Interceptor that executes the evaluation before the test method runs.
     */
    private static class EvaluationInvocationInterceptor implements InvocationInterceptor {

        @Override
        public void interceptTestTemplateMethod(
                Invocation<Void> invocation,
                ReflectiveInvocationContext<Method> invocationContext,
                ExtensionContext extensionContext) throws Throwable {

            Method method = invocationContext.getExecutable();
            EvaluationTest annotation = method.getAnnotation(EvaluationTest.class);

            if (annotation == null) {
                // Not an @EvaluationTest, proceed normally
                invocation.proceed();
                return;
            }

            // Load samples
            Samples<Object> samples = SampleLoaderResolver.load(annotation.samples(), Object.class);

            // Filter by tags if specified
            if (annotation.tags().length > 0) {
                samples = samples.filterByTags(annotation.tags());
            }

            // Get evaluation function
            Function<Parameters, Object> function = resolveEvaluationFunction(
                    annotation.function(),
                    extensionContext);

            // Instantiate evaluation strategy
            EvaluationStrategy<Object> strategy = instantiateStrategy(annotation.strategy());

            // Run evaluation
            Scorer scorer = new Scorer();
            EvaluationReport<Object> report;
            try {
                report = scorer.evaluate(samples, function, strategy);
            } finally {
                scorer.close();
            }

            // Generate reports if specified
            if (annotation.reportFormats().length > 0) {
                generateReports(report, annotation, method.getName());
            }

            // Validate score
            assertThat(report).hasScoreGreaterThanOrEqualTo(annotation.minScore());

            // Proceed with the (empty) test method
            invocation.proceed();
        }
    }

    /**
     * Resolve an evaluation function by name from the test class.
     */
    @SuppressWarnings("unchecked")
    private static Function<Parameters, Object> resolveEvaluationFunction(
            String functionName,
            ExtensionContext extensionContext) {

        Optional<Class<?>> maybeClass = extensionContext.getTestClass();
        if (maybeClass.isEmpty()) {
            throw new IllegalStateException("No test class found");
        }

        Class<?> testClass = maybeClass.get();
        Object testInstance = extensionContext.getRequiredTestInstance();

        // Find method annotated with @EvaluationFunction matching the name
        List<Method> methods = ReflectionSupport.findMethods(
                testClass,
                method -> {
                    if (!method.isAnnotationPresent(EvaluationFunction.class)) {
                        return false;
                    }
                    EvaluationFunction annot = method.getAnnotation(EvaluationFunction.class);
                    String name = annot.value().isEmpty() ? method.getName() : annot.value();
                    return name.equals(functionName);
                },
                HierarchyTraversalMode.TOP_DOWN);

        if (methods.isEmpty()) {
            throw new IllegalStateException(
                    String.format("No @EvaluationFunction method found with name '%s'", functionName));
        }

        if (methods.size() > 1) {
            throw new IllegalStateException(
                    String.format("Multiple @EvaluationFunction methods found with name '%s'", functionName));
        }

        Method functionMethod = methods.get(0);
        try {
            functionMethod.setAccessible(true);
            return (Function<Parameters, Object>) functionMethod.invoke(testInstance);
        } catch (Exception e) {
            throw new IllegalStateException(
                    String.format("Failed to invoke @EvaluationFunction method '%s'", functionName), e);
        }
    }

    /**
     * Instantiate an evaluation strategy from its class.
     */
    @SuppressWarnings("unchecked")
    private static EvaluationStrategy<Object> instantiateStrategy(
            Class<? extends EvaluationStrategy<?>> strategyClass) {
        try {
            return (EvaluationStrategy<Object>) strategyClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    String.format("Failed to instantiate strategy class '%s'. " +
                            "Strategy must have a no-arg constructor.", strategyClass.getName()),
                    e);
        }
    }

    /**
     * Generate reports in the specified formats.
     */
    @SuppressWarnings("rawtypes")
    private static void generateReports(EvaluationReport report, EvaluationTest annotation, String defaultBaseName) {
        try {
            Path outputDir = Paths.get(annotation.reportOutputDir());
            Files.createDirectories(outputDir);

            String baseName = defaultBaseName;

            // Build configuration map
            Map<String, Object> formatterConfig = new HashMap<>();
            formatterConfig.put("includeDetails", true);
            formatterConfig.put("pretty", true);

            // Generate reports for each format
            for (String format : annotation.reportFormats()) {
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
            LOG.errorf(e, "Failed to create output directory: %s", annotation.reportOutputDir());
        }
    }
}

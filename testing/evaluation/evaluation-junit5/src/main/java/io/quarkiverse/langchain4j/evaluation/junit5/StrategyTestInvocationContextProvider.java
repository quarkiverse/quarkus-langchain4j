package io.quarkiverse.langchain4j.evaluation.junit5;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

/**
 * JUnit 5 extension provider for {@link StrategyTest} annotation.
 * <p>
 * This provider creates multiple test template invocations - one for each strategy
 * specified in the {@link StrategyTest} annotation.
 * </p>
 */
public class StrategyTestInvocationContextProvider implements TestTemplateInvocationContextProvider {

    private static final Logger LOG = Logger.getLogger(StrategyTestInvocationContextProvider.class);

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> method.isAnnotationPresent(StrategyTest.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        Method method = context.getRequiredTestMethod();
        StrategyTest annotation = method.getAnnotation(StrategyTest.class);

        if (annotation == null) {
            return Stream.empty();
        }

        Class<? extends EvaluationStrategy<?>>[] strategies = annotation.strategies();
        if (strategies.length == 0) {
            throw new IllegalStateException("@StrategyTest must specify at least one strategy");
        }

        // Create one invocation context per strategy
        return Arrays.stream(strategies)
                .map(strategyClass -> new StrategyTestInvocationContext(strategyClass, annotation.minScore()));
    }

    /**
     * Custom invocation context for a specific strategy.
     */
    private static class StrategyTestInvocationContext implements TestTemplateInvocationContext {

        private final Class<? extends EvaluationStrategy<?>> strategyClass;
        private final double minScore;
        private EvaluationStrategy<?> strategyInstance;

        StrategyTestInvocationContext(
                Class<? extends EvaluationStrategy<?>> strategyClass,
                double minScore) {
            this.strategyClass = strategyClass;
            this.minScore = minScore;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return String.format("[%s]", strategyClass.getSimpleName());
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return Arrays.asList(
                    new StrategyParameterResolver(),
                    new MinScoreValidator());
        }

        /**
         * Parameter resolver that injects the strategy instance into test method parameters.
         */
        private class StrategyParameterResolver implements ParameterResolver {

            @Override
            public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
                Parameter parameter = parameterContext.getParameter();
                Class<?> parameterType = parameter.getType();

                // Support EvaluationStrategy parameters
                return EvaluationStrategy.class.isAssignableFrom(parameterType);
            }

            @Override
            public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
                // Lazy instantiation
                if (strategyInstance == null) {
                    strategyInstance = instantiateStrategy(strategyClass);
                }
                return strategyInstance;
            }
        }

        /**
         * Interceptor that validates the minimum score if configured.
         */
        private class MinScoreValidator implements InvocationInterceptor {

            @Override
            public void interceptTestTemplateMethod(
                    Invocation<Void> invocation,
                    ReflectiveInvocationContext<Method> invocationContext,
                    ExtensionContext extensionContext) throws Throwable {

                // Execute the test method
                invocation.proceed();

                // Validate minScore if specified
                if (minScore > 0.0) {
                    validateMinScore(extensionContext);
                }
            }

            private void validateMinScore(ExtensionContext extensionContext) {
                Object testInstance = extensionContext.getRequiredTestInstance();
                Class<?> testClass = testInstance.getClass();

                // Find fields annotated with @ReportConfiguration
                List<Field> reportFields = ReflectionSupport.findFields(
                        testClass,
                        field -> field.isAnnotationPresent(ReportConfiguration.class)
                                && EvaluationReport.class.isAssignableFrom(field.getType()),
                        HierarchyTraversalMode.TOP_DOWN);

                if (reportFields.isEmpty()) {
                    LOG.warnf("@StrategyTest with minScore=%f but no @ReportConfiguration field found in test class %s",
                            minScore, testClass.getName());
                    return;
                }

                // Validate score for each report field
                for (Field field : reportFields) {
                    field.setAccessible(true);
                    try {
                        EvaluationReport<?> report = (EvaluationReport<?>) field.get(testInstance);
                        if (report != null) {
                            assertThat(report).hasScoreGreaterThanOrEqualTo(minScore);
                        }
                    } catch (IllegalAccessException e) {
                        LOG.errorf(e, "Failed to access @ReportConfiguration field: %s", field.getName());
                    }
                }
            }
        }
    }

    /**
     * Instantiate a strategy from its class.
     */
    private static EvaluationStrategy<?> instantiateStrategy(
            Class<? extends EvaluationStrategy<?>> strategyClass) {
        try {
            return strategyClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    String.format("Failed to instantiate strategy class '%s'. " +
                            "Strategy must have a no-arg constructor.", strategyClass.getName()),
                    e);
        }
    }
}

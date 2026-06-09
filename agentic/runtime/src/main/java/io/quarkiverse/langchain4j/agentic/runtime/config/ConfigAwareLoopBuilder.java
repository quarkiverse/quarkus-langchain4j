package io.quarkiverse.langchain4j.agentic.runtime.config;

import java.util.Collection;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.LoopAgentService;

/**
 * TEMPORARY WORKAROUND — will be removed when upstream provides
 * workflow-level AgentConfigurator (see langchain4j/langchain4j#5399).
 *
 * Fluent decorator over {@link LoopAgentService}. Every fluent method
 * delegates to the real builder AND returns {@code this} to prevent
 * chain escape. On {@code build()}, applies the config-resolved
 * maxIterations before delegating.
 */
final class ConfigAwareLoopBuilder<T> implements LoopAgentService<T> {

    private final LoopAgentService<T> delegate;
    private final Optional<Integer> configMaxIterations;

    ConfigAwareLoopBuilder(LoopAgentService<T> delegate, Optional<Integer> configMaxIterations) {
        this.delegate = delegate;
        this.configMaxIterations = configMaxIterations;
    }

    @Override
    public T build() {
        configMaxIterations.ifPresent(delegate::maxIterations);
        return delegate.build();
    }

    @Override
    public LoopAgentService<T> maxIterations(int maxIterations) {
        delegate.maxIterations(maxIterations);
        return this;
    }

    @Override
    public LoopAgentService<T> exitCondition(Predicate<AgenticScope> exitCondition) {
        delegate.exitCondition(exitCondition);
        return this;
    }

    @Override
    public LoopAgentService<T> exitCondition(BiPredicate<AgenticScope, Integer> exitCondition) {
        delegate.exitCondition(exitCondition);
        return this;
    }

    @Override
    public LoopAgentService<T> exitCondition(String exitConditionDescription,
            Predicate<AgenticScope> exitCondition) {
        delegate.exitCondition(exitConditionDescription, exitCondition);
        return this;
    }

    @Override
    public LoopAgentService<T> exitCondition(String exitConditionDescription,
            BiPredicate<AgenticScope, Integer> exitCondition) {
        delegate.exitCondition(exitConditionDescription, exitCondition);
        return this;
    }

    @Override
    public LoopAgentService<T> testExitAtLoopEnd(boolean checkExitConditionAtLoopEnd) {
        delegate.testExitAtLoopEnd(checkExitConditionAtLoopEnd);
        return this;
    }

    @Override
    public LoopAgentService<T> subAgents(Object... agents) {
        delegate.subAgents(agents);
        return this;
    }

    @Override
    public LoopAgentService<T> subAgents(Collection<?> agents) {
        delegate.subAgents(agents);
        return this;
    }

    @Override
    public LoopAgentService<T> beforeCall(Consumer<AgenticScope> beforeCall) {
        delegate.beforeCall(beforeCall);
        return this;
    }

    @Override
    public LoopAgentService<T> name(String name) {
        delegate.name(name);
        return this;
    }

    @Override
    public LoopAgentService<T> description(String description) {
        delegate.description(description);
        return this;
    }

    @Override
    public LoopAgentService<T> outputKey(String outputKey) {
        delegate.outputKey(outputKey);
        return this;
    }

    @Override
    public LoopAgentService<T> outputKey(Class<? extends TypedKey<?>> outputKey) {
        delegate.outputKey(outputKey);
        return this;
    }

    @Override
    public LoopAgentService<T> output(Function<AgenticScope, Object> output) {
        delegate.output(output);
        return this;
    }

    @Override
    public LoopAgentService<T> errorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler) {
        delegate.errorHandler(errorHandler);
        return this;
    }

    @Override
    public LoopAgentService<T> listener(AgentListener listener) {
        delegate.listener(listener);
        return this;
    }
}

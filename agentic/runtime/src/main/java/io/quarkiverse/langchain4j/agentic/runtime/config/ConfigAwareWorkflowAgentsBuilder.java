package io.quarkiverse.langchain4j.agentic.runtime.config;

import java.util.Map;
import java.util.Optional;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.ParallelMapperService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.agentic.workflow.WorkflowAgentsBuilder;
import io.quarkiverse.langchain4j.agentic.runtime.AgenticRuntimeConfig;

/**
 * TEMPORARY WORKAROUND — will be removed when upstream provides
 * workflow-level AgentConfigurator (see langchain4j/langchain4j#5399).
 *
 * Wraps the default {@link WorkflowAgentsBuilder} and intercepts
 * {@code loopBuilder(Class)} to apply config-resolved {@code maxIterations}
 * via {@link ConfigAwareLoopBuilder}.
 */
public final class ConfigAwareWorkflowAgentsBuilder implements WorkflowAgentsBuilder {

    private final WorkflowAgentsBuilder delegate;
    private final AgenticRuntimeConfig config;
    private final Map<String, String> classNameToConfigKey;

    public ConfigAwareWorkflowAgentsBuilder(WorkflowAgentsBuilder delegate,
            AgenticRuntimeConfig config,
            Map<String, String> classNameToConfigKey) {
        this.delegate = delegate;
        this.config = config;
        this.classNameToConfigKey = classNameToConfigKey;
    }

    @Override
    public <T> LoopAgentService<T> loopBuilder(Class<T> agentServiceClass) {
        LoopAgentService<T> real = delegate.loopBuilder(agentServiceClass);
        Optional<Integer> configMaxIterations = resolveMaxIterations(agentServiceClass);
        return new ConfigAwareLoopBuilder<>(real, configMaxIterations);
    }

    @Override
    public LoopAgentService<UntypedAgent> loopBuilder() {
        return delegate.loopBuilder();
    }

    @Override
    public <T> SequentialAgentService<T> sequenceBuilder(Class<T> c) {
        return delegate.sequenceBuilder(c);
    }

    @Override
    public SequentialAgentService<UntypedAgent> sequenceBuilder() {
        return delegate.sequenceBuilder();
    }

    @Override
    public <T> ParallelAgentService<T> parallelBuilder(Class<T> c) {
        return delegate.parallelBuilder(c);
    }

    @Override
    public ParallelAgentService<UntypedAgent> parallelBuilder() {
        return delegate.parallelBuilder();
    }

    @Override
    public <T> ConditionalAgentService<T> conditionalBuilder(Class<T> c) {
        return delegate.conditionalBuilder(c);
    }

    @Override
    public ConditionalAgentService<UntypedAgent> conditionalBuilder() {
        return delegate.conditionalBuilder();
    }

    @Override
    public <T> ParallelMapperService<T> parallelMapperBuilder(Class<T> c) {
        return delegate.parallelMapperBuilder(c);
    }

    @Override
    public ParallelMapperService<UntypedAgent> parallelMapperBuilder() {
        return delegate.parallelMapperBuilder();
    }

    private <T> Optional<Integer> resolveMaxIterations(Class<T> agentServiceClass) {
        String configKey = classNameToConfigKey.get(agentServiceClass.getName());
        if (configKey == null) {
            return Optional.empty();
        }
        var agentConfig = config.namedConfig().get(configKey);
        if (agentConfig != null && agentConfig.maxIterations().isPresent()) {
            return agentConfig.maxIterations();
        }
        return Optional.empty();
    }
}

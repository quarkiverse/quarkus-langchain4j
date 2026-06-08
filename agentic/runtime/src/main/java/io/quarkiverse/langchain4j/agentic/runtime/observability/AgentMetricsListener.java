package io.quarkiverse.langchain4j.agentic.runtime.observability;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

@ApplicationScoped
public class AgentMetricsListener implements AgentListener {

    private final ConcurrentHashMap<String, Long> startTimes = new ConcurrentHashMap<>();

    private final Meter.MeterProvider<Counter> invocations;
    private final Meter.MeterProvider<Timer> duration;
    private final Meter.MeterProvider<Counter> toolExecutions;

    public AgentMetricsListener() {
        this.invocations = Counter.builder("gen_ai.agent.invocations")
                .description("Number of agent invocations")
                .withRegistry(Metrics.globalRegistry);
        this.duration = Timer.builder("gen_ai.agent.duration")
                .description("Agent invocation duration")
                .withRegistry(Metrics.globalRegistry);
        this.toolExecutions = Counter.builder("gen_ai.agent.tool.executions")
                .description("Number of tool executions by agents")
                .withRegistry(Metrics.globalRegistry);
    }

    @Override
    public void beforeAgentInvocation(AgentRequest agentRequest) {
        startTimes.put(agentRequest.agentId(), System.nanoTime());
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        Tags tags = Tags.of("gen_ai.agent.name", agentResponse.agentName())
                .and("error.type", "none");
        invocations.withTags(tags).increment();
        recordDuration(agentResponse.agentId(), tags);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError invocationError) {
        String errorType = invocationError.error() != null
                ? invocationError.error().getClass().getSimpleName()
                : "unknown";
        Tags tags = Tags.of("gen_ai.agent.name", invocationError.agentName())
                .and("error.type", errorType);
        invocations.withTags(tags).increment();
        recordDuration(invocationError.agentId(), tags);
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution afterToolExecution) {
        Tags tags = Tags.of(
                "gen_ai.agent.name", afterToolExecution.agentInstance().name(),
                "gen_ai.tool.name", afterToolExecution.toolExecution().request().name());
        toolExecutions.withTags(tags).increment();
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    private void recordDuration(String agentId, Tags tags) {
        Long startTime = startTimes.remove(agentId);
        if (startTime != null) {
            duration.withTags(tags).record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }
}

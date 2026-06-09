package io.quarkiverse.langchain4j.agentic.runtime.observability;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;

@ApplicationScoped
public class AgentCdiEventListener implements AgentListener {

    private final ConcurrentHashMap<String, Long> startTimes = new ConcurrentHashMap<>();

    @Inject
    Event<AgentStartedEvent> startedEvent;

    @Inject
    Event<AgentCompletedEvent> completedEvent;

    @Inject
    Event<AgentErrorEvent> errorEvent;

    @Override
    public void beforeAgentInvocation(AgentRequest agentRequest) {
        startTimes.put(agentRequest.agentId(), System.nanoTime());
        AgentStartedEvent event = new AgentStartedEvent(
                agentRequest.agentName(),
                agentRequest.agentId(),
                agentRequest.agenticScope().memoryId(),
                agentRequest.inputs());
        startedEvent.select(AgentSelectorLiteral.of(agentRequest.agent().type())).fire(event);
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        Long startTime = startTimes.remove(agentResponse.agentId());
        long durationNanos = startTime != null ? System.nanoTime() - startTime : 0;

        Optional<TokenUsage> tokenUsage = Optional.ofNullable(agentResponse.chatResponse())
                .map(ChatResponse::metadata)
                .map(ChatResponseMetadata::tokenUsage);

        AgentCompletedEvent event = new AgentCompletedEvent(
                agentResponse.agentName(),
                agentResponse.agentId(),
                agentResponse.agenticScope().memoryId(),
                agentResponse.inputs(),
                agentResponse.output(),
                durationNanos,
                tokenUsage);
        completedEvent.select(AgentSelectorLiteral.of(agentResponse.agent().type())).fire(event);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        startTimes.remove(agentInvocationError.agentId());
        AgentErrorEvent event = new AgentErrorEvent(
                agentInvocationError.agentName(),
                agentInvocationError.agentId(),
                agentInvocationError.agenticScope().memoryId(),
                agentInvocationError.inputs(),
                agentInvocationError.error());
        errorEvent.select(AgentSelectorLiteral.of(agentInvocationError.agent().type())).fire(event);
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }
}

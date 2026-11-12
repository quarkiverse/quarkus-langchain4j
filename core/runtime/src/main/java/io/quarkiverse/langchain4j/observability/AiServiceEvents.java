package io.quarkiverse.langchain4j.observability;

import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import io.quarkiverse.langchain4j.observability.listener.AiServiceListenerAdapter;

/**
 * Enum representing various events that can occur in an AI service. Each event type
 * is associated with a specific event class that extends {@link AiServiceEvent}.
 */
public enum AiServiceEvents {
    CANCELLED(AiServiceCancelledEvent.class),
    COMPLETED(AiServiceCompletedEvent.class),
    ERROR(AiServiceErrorEvent.class),
    INPUT_GUARDRAIL_EXECUTED(InputGuardrailExecutedEvent.class),
    /**
     * A composite event that the registrar synthesises from all the other events of an invocation. The registrar only
     * records those events while a listener for this event is registered, so unlike every other constant here this
     * listener is registered only when the application actually observes the event.
     */
    INVOCATION_SUMMARY(AiServiceInvocationSummaryEvent.class),
    OUTPUT_GUARDRAIL_EXECUTED(OutputGuardrailExecutedEvent.class),
    REQUEST_ISSUED(AiServiceRequestIssuedEvent.class),
    RESPONSE_RECEIVED(AiServiceResponseReceivedEvent.class),
    RETRIEVAL_AUGMENTED(RetrievalAugmentedEvent.class),
    STARTED(AiServiceStartedEvent.class),
    TOOL_EXECUTED(ToolExecutedEvent.class);

    private final Class<? extends AiServiceEvent> eventClass;

    AiServiceEvents(Class<? extends AiServiceEvent> eventClass) {
        this.eventClass = eventClass;
    }

    public <T extends AiServiceEvent> AiServiceListenerAdapter<T> createListener(Class<?> aiServiceClass) {
        return new AiServiceListenerAdapter<>(getEventClass(), aiServiceClass);
    }

    public <T extends AiServiceEvent> Class<T> getEventClass() {
        return (Class<T>) this.eventClass;
    }
}

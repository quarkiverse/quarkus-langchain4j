package io.quarkiverse.langchain4j.observability.listener;

import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import io.quarkiverse.langchain4j.observability.AiServiceSelectorLiteral;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * An adapter class for implementing the {@link AiServiceListener} interface to CDI event firing.
 *
 * @param <T> the type of {@link AiServiceEvent} being observed, which extends {@link AiServiceEvent}
 */
public record AiServiceListenerAdapter<T extends AiServiceEvent>(Class<T> eventClass,
        Class<?> aiServiceClass) implements AiServiceListener<T> {
    @Override
    public Class<T> getEventClass() {
        return this.eventClass;
    }

    @Override
    public void onEvent(T event) {
        ArcContainer container = Arc.container();
        if (container == null) { // can happen during shutdown
            return;
        }
        container
                .beanManager()
                .getEvent()
                .select(
                        getEventClass(),
                        AiServiceSelectorLiteral.builder()
                                .aiServiceClass(this.aiServiceClass)
                                .build())
                .fire(event);
    }
}

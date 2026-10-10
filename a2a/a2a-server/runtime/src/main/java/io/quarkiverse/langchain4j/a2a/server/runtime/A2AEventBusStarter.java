package io.quarkiverse.langchain4j.a2a.server.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.a2aproject.sdk.server.events.MainEventBusProcessor;

import io.quarkus.runtime.StartupEvent;

/**
 * Starts the A2A event bus processor once the application is running.
 * <p>
 * The SDK ships {@code MainEventBusProcessorInitializer}, which does this from an
 * {@code @Initialized(ApplicationScoped.class)} observer. That event is portable, but Quarkus fires it during
 * static initialization, so the processor's background thread would be started while the application is being
 * built — which a native image build rejects with "Detected a started Thread in the image heap". The extension
 * vetoes that initializer and starts the processor from {@link StartupEvent} instead, which runs at runtime.
 */
@ApplicationScoped
public class A2AEventBusStarter {

    private final MainEventBusProcessor processor;

    public A2AEventBusStarter(MainEventBusProcessor processor) {
        this.processor = processor;
    }

    void onStart(@Observes StartupEvent event) {
        processor.start();
    }
}

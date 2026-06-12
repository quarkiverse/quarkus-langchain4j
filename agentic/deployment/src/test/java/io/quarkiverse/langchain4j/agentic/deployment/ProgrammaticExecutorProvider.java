package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.concurrent.Executor;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import dev.langchain4j.spi.ExecutorProvider;

@Singleton
public class ProgrammaticExecutorProvider implements ExecutorProvider {

    static int executorCalls;

    // Register before the core RUNTIME_INIT recorder.
    void register(@Observes @Initialized(ApplicationScoped.class) Object event) {
        ExecutorProvider.set(this);
    }

    @PreDestroy
    void clear() {
        if (ExecutorProvider.get() == this) {
            ExecutorProvider.set(null);
        }
    }

    @Override
    public Executor executor() {
        executorCalls++;
        return new CustomExecutorProviderTestSupport.NamedExecutor("programmatic-provider-worker");
    }
}

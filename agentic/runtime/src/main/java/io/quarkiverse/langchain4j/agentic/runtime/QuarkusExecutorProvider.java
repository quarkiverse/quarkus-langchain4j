package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.concurrent.Executor;

import dev.langchain4j.spi.ExecutorProvider;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodImplementationSupport;

/**
 * Provides langchain4j-agentic with the executor the core extension uses to execute tools concurrently, which runs
 * the parallel and asynchronous agents on virtual threads when available and propagates the request context to them.
 */
public class QuarkusExecutorProvider implements ExecutorProvider {

    private final Executor executor = AiServiceMethodImplementationSupport.createToolExecutor();

    @Override
    public Executor executor() {
        return executor;
    }
}

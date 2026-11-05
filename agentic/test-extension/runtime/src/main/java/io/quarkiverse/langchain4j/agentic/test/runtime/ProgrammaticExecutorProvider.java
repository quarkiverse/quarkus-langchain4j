package io.quarkiverse.langchain4j.agentic.test.runtime;

import java.util.concurrent.Executor;

import dev.langchain4j.spi.ExecutorProvider;

public class ProgrammaticExecutorProvider implements ExecutorProvider {

    public static int executorCalls;

    @Override
    public Executor executor() {
        executorCalls++;
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                Thread thread = new Thread(command, "programmatic-provider-worker");
                thread.setDaemon(true);
                thread.start();
            }
        };
    }
}

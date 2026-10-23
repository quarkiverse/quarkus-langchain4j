package io.quarkiverse.langchain4j.agentic.test.runtime;

import dev.langchain4j.spi.ExecutorProvider;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ProgrammaticExecutorProviderRecorder {

    public void register() {
        ExecutorProvider.set(new ProgrammaticExecutorProvider());
    }
}

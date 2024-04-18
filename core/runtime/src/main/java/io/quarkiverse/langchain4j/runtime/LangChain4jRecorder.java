package io.quarkiverse.langchain4j.runtime;

import io.quarkiverse.langchain4j.QuarkusPromptTemplateFactory;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class LangChain4jRecorder {

    public void cleanUp(ShutdownContext shutdown) {
        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                StructuredPromptsRecorder.clearTemplates();
                QuarkusPromptTemplateFactory.clear();
                AiServicesRecorder.clearMetadata();
                ToolsRecorder.clearMetadata();
            }
        });
    }
}

package io.quarkiverse.langchain4j;

import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.spi.services.AiServiceContextFactory;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;

public class QuarkusAiServiceContextFactory implements AiServiceContextFactory {

    @Override
    public AiServiceContext create(Class<?> aiServiceClass) {
        return new QuarkusAiServiceContext(aiServiceClass);
    }
}

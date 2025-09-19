package io.quarkiverse.langchain4j;

import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.spi.services.AiServiceContextFactory;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContextQualifier;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

public class QuarkusAiServiceContextFactory implements AiServiceContextFactory {

    @Override
    public AiServiceContext create(Class<?> aiServiceClass) {
        InstanceHandle<QuarkusAiServiceContext> instance = Arc.container().instance(QuarkusAiServiceContext.class,
                QuarkusAiServiceContextQualifier.Literal.of(
                        aiServiceClass.getName()));
        if (instance.isAvailable()) {
            return instance.get();
        }
        return new QuarkusAiServiceContext(aiServiceClass); // just create a default context
    }
}

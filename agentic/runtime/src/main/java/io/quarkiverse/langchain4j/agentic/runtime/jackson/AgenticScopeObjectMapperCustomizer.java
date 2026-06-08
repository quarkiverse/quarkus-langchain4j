package io.quarkiverse.langchain4j.agentic.runtime.jackson;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkus.jackson.ObjectMapperCustomizer;

/**
 * Registers {@link AgenticScopeJacksonModule} on the application's managed {@link ObjectMapper} so agentic
 * state can be marshalled transparently — for instance when a workflow engine persists it.
 * <p>
 * Without this, any code using the Quarkus-managed ObjectMapper to serialize an {@link AgenticScope}
 * (directly or nested in another object) fails, because {@link DefaultAgenticScope} exposes no
 * Jackson-discoverable properties.
 */
@Singleton
public class AgenticScopeObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(AgenticScopeJacksonModule.INSTANCE);
    }
}

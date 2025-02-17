package io.quarkiverse.langchain4j.ai.runtime.gemini;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public record FunctionCall(String name, Map<String, Object> args) {
    public ToolExecutionRequest toToolExecutionRequest() {
        try {
            return ToolExecutionRequest.builder()
                    .name(name())
                    .arguments(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER
                            .writeValueAsString(args()))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to parse tool call response", e);
        }
    }
}

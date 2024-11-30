package io.quarkiverse.langchain4j.ollama;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public record ToolCall(FunctionCall function) {

    public static ToolCall fromFunctionCall(String name, Map<String, Object> arguments) {
        return new ToolCall(new FunctionCall(name, arguments));
    }

    public ToolExecutionRequest toToolExecutionRequest() {
        try {
            return ToolExecutionRequest.builder()
                    .name(function.name)
                    .arguments(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER
                            .writeValueAsString(function.arguments()))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to parse tool call response", e);
        }
    }

    public record FunctionCall(String name, Map<String, Object> arguments) {

    }
}

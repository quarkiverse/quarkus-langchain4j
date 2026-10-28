package io.quarkiverse.langchain4j.mockapi;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A dialect-neutral view of a recorded chat request.
 */
public interface ChatRequestView {

    String modelName();

    boolean stream();

    Double temperature();

    Double topP();

    Integer maxTokens();

    List<Message> messages();

    List<ToolDefinition> tools();

    String toolChoice();

    String responseFormatType();

    JsonNode responseFormatSchema();

    interface Message {

        String role();

        String text();

        List<ToolCall> toolCalls();

        String toolCallId();

        String name();
    }

    interface ToolDefinition {

        String name();

        String description();

        JsonNode parametersSchema();
    }
}

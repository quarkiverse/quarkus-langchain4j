package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import dev.langchain4j.data.message.ChatMessageType;

final class RoleMapper {

    private RoleMapper() {
    }

    static String map(ChatMessageType type) {
        return switch (type) {
            case USER -> "user";
            case AI -> "model";
            case TOOL_EXECUTION_RESULT -> null;
            default -> throw new IllegalArgumentException(type + " is not allowed.");
        };
    }
}

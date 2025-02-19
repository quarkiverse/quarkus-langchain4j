package io.quarkiverse.langchain4j.gemini.common;

import dev.langchain4j.data.message.ChatMessageType;

final class RoleMapper {

    private RoleMapper() {
    }

    static String map(ChatMessageType type) {
        return switch (type) {
            case USER -> "user";
            case AI -> "model";
            case TOOL_EXECUTION_RESULT -> "user";
            default -> throw new IllegalArgumentException(type + " is not allowed.");
        };
    }
}

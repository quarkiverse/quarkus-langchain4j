package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatter;

/**
 * Mistral prompt formatter.
 */
public class MistralPromptFormatter implements PromptFormatter {

    @Override
    public String joiner() {
        return "";
    }

    @Override
    public String start() {
        return "<s>";
    }

    @Override
    public String system() {
        return "[INST] ";
    }

    @Override
    public String user() {
        return "[INST] ";
    }

    @Override
    public String assistant() {
        return "";
    }

    @Override
    public String endOf(ChatMessage chatMessage) {
        return switch (chatMessage.type()) {
            case AI -> "</s>";
            case SYSTEM -> " [/INST]</s>";
            case USER -> " [/INST]";
            case TOOL_EXECUTION_RESULT -> "";
        };
    }

    @Override
    public String format(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return """
                %s\
                %s\
                %s\
                """.formatted(start(), systemMessageFormatter(messages), messagesFormatter(messages));
    }
}

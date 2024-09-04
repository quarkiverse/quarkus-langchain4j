package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatter;

/**
 * Granite code prompt formatter.
 */
public class GraniteCodePromptFormatter implements PromptFormatter {

    @Override
    public String system() {
        return "System:\n";
    }

    @Override
    public String user() {
        return "Question:\n";
    }

    @Override
    public String assistant() {
        return "Answer:\n";
    }

    @Override
    public String endOf(ChatMessage chatMessage) {
        return "\n";
    }

    @Override
    public String format(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return """
                %s\
                %s\
                """.formatted(systemMessageFormatter(messages), messagesFormatter(messages));
    }
}

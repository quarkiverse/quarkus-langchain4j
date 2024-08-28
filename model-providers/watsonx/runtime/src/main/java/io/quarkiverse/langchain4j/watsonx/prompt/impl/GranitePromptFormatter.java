package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatter;

/**
 * Granite prompt formatter.
 */
public class GranitePromptFormatter implements PromptFormatter {

    @Override
    public String system() {
        return "<|system|>\n";
    }

    @Override
    public String user() {
        return "<|user|>\n";
    }

    @Override
    public String assistant() {
        return "<|assistant|>\n";
    }

    @Override
    public String endOf(ChatMessageType messageType) {
        return "";
    }

    @Override
    public String format(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return """
                %s\
                %s\
                """.formatted(systemMessageFormatter(messages), messagesFormatter(messages));
    }
}

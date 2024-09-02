package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatter;

/**
 * Llama 3.x prompt formatter.
 */
public class LlamaPromptFormatter implements PromptFormatter {

    @Override
    public String joiner() {
        return "";
    }

    @Override
    public String start() {
        return "<|begin_of_text|>";
    }

    @Override
    public String system() {
        return "<|start_header_id|>system<|end_header_id|>\n\n";
    }

    @Override
    public String user() {
        return "<|start_header_id|>user<|end_header_id|>\n";
    }

    @Override
    public String assistant() {
        return "<|start_header_id|>assistant<|end_header_id|>\n";
    }

    @Override
    public String format(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return """
                %s\
                %s\
                %s\
                """.formatted(start(), systemMessageFormatter(messages), messagesFormatter(messages));
    }

    @Override
    public String endOf(ChatMessageType type) {
        return "<|eot_id|>";
    }
}

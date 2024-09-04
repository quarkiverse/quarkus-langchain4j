package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import java.util.List;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatter;

/*
 * Prompt formatter used when the model used doesn't have a specific implementation or when the prompt-formatter property is set
 * to false.
 */
public class NoopPromptFormatter implements PromptFormatter {

    private String joiner;

    public NoopPromptFormatter(String joiner) {
        this.joiner = joiner;
    }

    @Override
    public String joiner() {
        return joiner;
    }

    @Override
    public String system() {
        return "";
    }

    @Override
    public String user() {
        return "";
    }

    @Override
    public String assistant() {
        return "";
    }

    @Override
    public String endOf(ChatMessage chatMessage) {
        return "";
    }

    @Override
    public String format(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return messages.stream().map(ChatMessage::text).collect(Collectors.joining(joiner()));
    }
}

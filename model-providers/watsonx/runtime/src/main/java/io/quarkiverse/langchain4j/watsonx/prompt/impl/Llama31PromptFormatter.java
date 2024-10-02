package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;

import jakarta.json.JsonValue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptToolFormatter;

/**
 * Llama 3.x prompt formatter.
 */
public class Llama31PromptFormatter extends LlamaPromptFormatter {

    private static final LlamaToolFormatter toolFormatter = new LlamaToolFormatter();

    @Override
    public PromptToolFormatter promptToolFormatter() {
        return toolFormatter;
    }

    @Override
    public String toolResult() {
        return "<|start_header_id|>ipython<|end_header_id|>\n\n";
    }

    @Override
    public String toolExecution() {
        return "<|python_tag|>";
    }

    @Override
    public String endOf(ChatMessage chatMessage) {
        return switch (chatMessage.type()) {
            case AI -> {
                var aiMessage = (AiMessage) chatMessage;
                var tag = aiMessage.hasToolExecutionRequests() ? "<|eom_id|>" : "<|eot_id|>";
                yield tag;
            }
            case SYSTEM, USER, TOOL_EXECUTION_RESULT -> "<|eot_id|>";
        };
    }

    @Override
    public String format(List<ChatMessage> messages, List<ToolSpecification> tools) {

        var systemMessage = systemMessageFormatter(messages);

        if (tools != null && tools.size() > 0) {
            return """
                    <|begin_of_text|><|start_header_id|>system<|end_header_id|>

                    Environment: ipython
                    Cutting Knowledge Date: December 2023
                    Today Date: 26 Jul 2024

                    You have access to the following functions. \
                    To call a function, respond with JSON for a function call. \
                    When you access a function respond always in the format \
                    {"name": function name, "parameters": dictionary of argument name and its value}. Do not use variables.

                    %s

                    %s<|eot_id|>%s"""
                    .formatted(
                            toolsFormatter(tools).stream().map(JsonValue::toString).collect(joining("\n\n")),
                            systemMessage,
                            messagesFormatter(messages));
        }

        // Prompt without tools.
        systemMessage = systemMessage.isBlank() ? "" : system() + systemMessage + "<|eot_id|>";
        return "%s%s%s".formatted(start(), systemMessage, messagesFormatter(messages));
    }

    @Override
    public String systemMessageFormatter(List<ChatMessage> messages) {
        return messages.stream()
                .filter(new Predicate<ChatMessage>() {
                    @Override
                    public boolean test(ChatMessage message) {
                        return message.type().equals(SYSTEM);
                    }
                })
                .findFirst()
                .map(ChatMessage::text)
                .orElse("");
    }

    @Override
    public String messagesFormatter(List<ChatMessage> messages) {

        StringJoiner joiner = new StringJoiner(joiner());
        var lastMessage = messages.get(messages.size() - 1);

        for (ChatMessage message : messages) {

            if (message.type().equals(SYSTEM))
                continue;

            if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {

                joiner.add(tagOf(message) + promptToolFormatter().convert(toolExecutionResultMessage) + endOf(message));

            } else if (message instanceof AiMessage aiMessage) {

                if (aiMessage.hasToolExecutionRequests()) {
                    joiner.add(tagOf(message));
                    joiner.add(
                            toolExecution() + promptToolFormatter().convert(aiMessage.toolExecutionRequests())
                                    + endOf(message));
                } else {
                    joiner.add(tagOf(message) + message.text() + endOf(message));
                }

            } else {
                joiner.add(tagOf(message) + message.text() + endOf(message));
            }
        }

        if (lastMessage.type() != AI && !tagOf(AI).isBlank()) {
            joiner.add(tagOf(AI));
        }

        return joiner.toString();
    }

    @Override
    public List<ToolExecutionRequest> toolExecutionRequestFormatter(String json) {
        if (json.contains(";"))
            json = "[" + json.replaceAll(";", ",") + "]";

        return super.toolExecutionRequestFormatter(json);
    }
}

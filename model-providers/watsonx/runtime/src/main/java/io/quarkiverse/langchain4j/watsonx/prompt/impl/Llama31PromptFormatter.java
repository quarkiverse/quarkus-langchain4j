package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;

import jakarta.json.Json;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatterUtil;
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

                    You have access to the following functions. To call a function, respond with JSON for a function call. When you access a function respond always in the format {"name": function name, "parameters": dictionary of argument name and its value}. Do not use variables.

                    %s

                    %s<|eot_id|>%s"""
                    .formatted(toolsFormatter(tools), systemMessage, messagesFormatter(messages));
        }

        // Prompt without tools.
        systemMessage = systemMessage.isBlank() ? "" : system() + systemMessage + "<|eot_id|>";
        return """
                %s\
                %s\
                %s\
                """.formatted(start(), systemMessage, messagesFormatter(messages));
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
                .map(new Function<ChatMessage, String>() {
                    @Override
                    public String apply(ChatMessage message) {
                        return message.text();
                    }
                })
                .orElse("");
    }

    @Override
    public String messagesFormatter(List<ChatMessage> messages) {

        StringJoiner joiner = new StringJoiner(joiner(), "", "");
        var lastMessage = messages.get(messages.size() - 1);

        for (int i = 0; i < messages.size(); i++) {

            ChatMessage message = messages.get(i);

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

    /**
     * Formats a list of {@link ToolSpecification} objects into a JSON string.
     *
     * @param tools the list of tool specifications to be formatted.
     * @return a string representing the formatted tools in JSON format.
     */
    public String toolsFormatter(List<ToolSpecification> tools) {

        if (tools == null || tools.isEmpty())
            return "";

        var result = new StringJoiner("\n\n");
        for (ToolSpecification tool : tools) {

            var json = Json.createObjectBuilder().add("type", "function");
            var function = Json.createObjectBuilder()
                    .add("name", tool.name())
                    .add("description", tool.description());

            ToolParameters toolParameters = tool.parameters();
            var parameters = Json.createObjectBuilder();

            if (toolParameters != null && !toolParameters.properties().isEmpty()) {

                var properties = Json.createObjectBuilder();
                parameters.add("type", toolParameters.type());

                for (Map.Entry<String, Map<String, Object>> entry : toolParameters.properties().entrySet()) {
                    var key = entry.getKey();
                    var value = entry.getValue();
                    properties.add(key, PromptFormatterUtil.convert(value));
                }

                parameters.add("properties", properties.build());
            }

            var required = Json.createArrayBuilder();
            toolParameters.required().forEach(required::add);

            parameters.add("required", required);
            function.add("parameters", parameters);
            json.add("function", function);
            result.add(json.build().toString());
        }

        return result.toString();
    }

    @Override
    public List<ToolExecutionRequest> toolExecutionRequestFormatter(String json) {
        if (json.contains(";"))
            json = "[" + json.replaceAll(";", ",") + "]";

        return super.toolExecutionRequestFormatter(json);
    }
}

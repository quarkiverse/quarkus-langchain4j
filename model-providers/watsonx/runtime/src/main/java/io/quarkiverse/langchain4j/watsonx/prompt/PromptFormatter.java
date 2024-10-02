package io.quarkiverse.langchain4j.watsonx.prompt;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static jakarta.json.JsonValue.ValueType.ARRAY;
import static jakarta.json.JsonValue.ValueType.OBJECT;
import static java.util.function.Predicate.not;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

/**
 * The {@code PromptFormatter} interface defines the structure for handling and converting different types of
 * {@link ChatMessage}
 * objects into a specific string format.
 */
public interface PromptFormatter {

    /**
     * Returns an instance of {@link PromptToolFormatter} that is responsible for formatting tool-related data.
     * <p>
     * This method is intended to be overridden by implementations that allow the use of tools.
     *
     * @return an instance of {@link PromptToolFormatter}.
     */
    public default PromptToolFormatter promptToolFormatter() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Defines the string used to join multiple {@link ChatMessage} objects when constructing the prompt.
     *
     * @return the string used to join messages in the prompt.
     */
    public default String joiner() {
        return "\n";
    }

    /**
     * Defines a start tag that wraps the entire prompt.
     *
     * @return the start tag for the prompt.
     */
    public default String start() {
        return "";
    }

    /**
     * Defines an end tag that wraps the entire prompt.
     *
     * @return the end tag for the prompt.
     */
    public default String end() {
        return "";
    }

    /**
     * Returns the tag used to mark {@link SystemMessage} within the prompt.
     *
     * @return the tag representing a system message.
     */
    String system();

    /**
     * Returns the tag used to mark {@link UserMessage} within the prompt.
     *
     * @return the tag representing a user message.
     */
    String user();

    /**
     * Returns the tag used to mark {@link AiMessage} within the prompt.
     *
     * @return the tag representing an assistant message.
     */
    String assistant();

    /**
     * Returns the tag used to mark {@link ToolExecutionResultMessage} within the prompt.
     *
     * @return the tag representing a tool execution message.
     */
    default String toolResult() {
        return "";
    }

    /**
     * Returns the tag used by the LLM to request a {@link Tool} execution.
     *
     * @return the tag representing a tool request by the LLM.
     */
    default String toolExecution() {
        return "";
    }

    /**
     * Converts a list of {@link ChatMessage} objects and {@link ToolSpecification} objects into a formatted prompt.
     *
     * @param messages the list of chat messages to be formatted.
     * @param tools the list of tool specifications to be formatted.
     * @return a string representing the formatted prompt.
     */
    String format(List<ChatMessage> messages, List<ToolSpecification> tools);

    /**
     * Converts a list of {@link ChatMessage} into a formatted prompt.
     *
     * @param messages the list of chat messages to be formatted.
     * @return a string representing the formatted prompt.
     */
    default String format(List<ChatMessage> messages) {
        return format(messages, null);
    }

    /**
     * Defines how to close a tag based on the message.
     *
     * @param message the {@link ChatMessage} for which the closing tag is being requested.
     * @return the closing tag for the specified message type.
     */
    String endOf(ChatMessage message);

    /**
     * Returns the tag associated with a specific {@link ChatMessageType}.
     *
     * @param type the {@link ChatMessageType} for which the tag is being requested.
     * @return the tag for the specified message type.
     */
    default String tagOf(ChatMessageType type) {
        return switch (type) {
            case AI -> assistant();
            case SYSTEM -> system();
            case TOOL_EXECUTION_RESULT -> toolResult();
            case USER -> user();
        };
    }

    /**
     * Returns the tag associated with a specific {@link ChatMessage}.
     *
     * @param message the {@link ChatMessage} for which the tag is being requested.
     * @return the tag for the specified message.
     */
    default String tagOf(ChatMessage message) {
        return tagOf(message.type());
    }

    /**
     * Returns a list of all relevant tags used in the prompt.
     *
     * @return a list of all relevant tags used in the prompt.
     */
    default List<String> tokens() {
        return Stream.of(start(), end(), system(), user(), assistant(), toolResult())
                .map(String::trim)
                .filter(not(String::isBlank))
                .toList();
    }

    /**
     * Formats the system message from a list of {@link ChatMessage} objects.
     *
     * @param messages the list of chat messages from which the system message is formatted.
     * @return a string representing the formatted system message.
     */
    default String systemMessageFormatter(List<ChatMessage> messages) {
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
                        return system() + message.text() + endOf(message) + joiner();
                    }
                })
                .orElse("");
    }

    /**
     * Formats a list of {@link ChatMessage} objects into a string by concatenating each message with its corresponding tag.
     *
     * @param messages the list of chat messages to be formatted.
     * @return a string representing the formatted messages.
     */
    default String messagesFormatter(List<ChatMessage> messages) {

        StringJoiner joiner = new StringJoiner(joiner(), "", "");
        var lastMessage = messages.get(messages.size() - 1);

        for (ChatMessage message : messages) {

            if (message.type().equals(SYSTEM))
                continue;

            if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {

                joiner.add(
                        tagOf(message) + promptToolFormatter().convert(toolExecutionResultMessage) + endOf(message));

            } else if (message instanceof AiMessage aiMessage) {

                String text;

                if (aiMessage.hasToolExecutionRequests()) {
                    text = "%s%s%s".formatted(
                            toolExecution(),
                            promptToolFormatter().convert(aiMessage.toolExecutionRequests()),
                            endOf(message));
                } else {
                    text = tagOf(message) + message.text() + endOf(message);
                }

                joiner.add(text);

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
     * Formats a list of {@link ToolSpecification} objects into a {@link JsonArray}.
     *
     * @param tools the list of tool specifications to be formatted.
     * @return a {@link JsonArray} with tools in JSON format.
     */
    default JsonArray toolsFormatter(List<ToolSpecification> tools) {

        if (tools == null || tools.isEmpty())
            return Json.createArrayBuilder().build();

        var result = Json.createArrayBuilder();
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
            result.add(json);
        }

        return result.build();
    }

    /**
     * Parses a JSON string representing a {@List} of {@link ToolExecutionRequest} and converts it into a JSON representation.
     *
     * @param json the JSON string to parse
     * @return a {@List} of {@link ToolExecutionRequest} objects
     */
    default List<ToolExecutionRequest> toolExecutionRequestFormatter(String json) {

        List<ToolExecutionRequest> result = new ArrayList<>();
        StringReader stringReader = new StringReader(json);

        try (JsonReader jsonReader = Json.createReader(stringReader)) {
            var jsonValue = jsonReader.readValue();

            if (jsonValue.getValueType().equals(ARRAY)) {
                for (JsonValue toolExecutionRequest : jsonValue.asJsonArray()) {
                    result.add(promptToolFormatter().toolExecutionRequest(toolExecutionRequest));
                }
            } else if (jsonValue.getValueType().equals(OBJECT)) {
                result.add(promptToolFormatter().toolExecutionRequest(jsonValue.asJsonObject()));
            }
        }
        return result;
    }
}

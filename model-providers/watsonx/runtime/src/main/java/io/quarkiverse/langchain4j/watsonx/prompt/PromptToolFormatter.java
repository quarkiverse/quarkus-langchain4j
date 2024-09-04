package io.quarkiverse.langchain4j.watsonx.prompt;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonValue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

/**
 * The {@code PromptToolFormatter} interface defines the contract for formatting tool execution requests and results into JSON
 * representations. Implementations of this interface are responsible for converting tool messages to the JSON format required
 * by
 * the model.
 */
public interface PromptToolFormatter {

    /**
     * Converts a {@link ToolExecutionResultMessage} into a {@link String}. This method is responsible for transforming the
     * result of
     * a tool execution into a format that can be understood by the model.
     *
     * @param toolExecutionResultMessage the {@link ToolExecutionResultMessage} to convert
     * @return a {@code JsonValue} representing the JSON of a {@code ToolExecutionResultMessage}
     */
    public JsonValue convert(ToolExecutionResultMessage toolExecutionResultMessage);

    /**
     * Converts a list of {@link ToolExecutionRequest} objects into a JSON string representation.
     *
     * @param toolExecutionRequests the list of {@link ToolExecutionRequest} objects to convert
     * @return a {@code String} representing the JSON of a {@List} of {@code ToolExecutionRequest}
     */
    public default String convert(List<ToolExecutionRequest> toolExecutionRequests) {
        var result = Json.createArrayBuilder();
        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            result.add(convert(toolExecutionRequest));
        }
        return result.build().toString();
    }

    /**
     * Converts a {@link ToolExecutionRequest} into a {@link String}. This method is responsible for transforming a tool
     * execution
     * request into a format that can be understood by the model.
     *
     * @param toolExecutionRequest the {@link ToolExecutionRequest} to convert
     * @return a {@code JsonValue} representing the JSON of {@code ToolExecutionRequest}
     */
    public JsonValue convert(ToolExecutionRequest toolExecutionRequest);

    /**
     * Reconstructs a {@link ToolExecutionRequest} from a {@link JsonValue}. This method is responsible for creating a tool
     * execution
     * request object from its JSON representation.
     *
     * @param json the {@link JsonValue} representing the {@code ToolExecutionRequest}
     * @return a {@link ToolExecutionRequest} object constructed from the JSON value
     */
    public ToolExecutionRequest toolExecutionRequest(JsonValue json);
}

package io.quarkiverse.langchain4j.vertexai.runtime.models;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public class GenerateResponseHandler {

    /**
     * Return the reason why the model stopped using the field: stop_sequence from the GenerateResponse
     *
     * @param response The GenerateResponse
     * @return The FinishReason
     */
    public static FinishReason getFinishReason(GenerateResponse response) {
        return AnthropicMapper.toFinishReason(response.stop_sequence());
    }

    /**
     * Extract from the Response the text where the Content type is "text"
     *
     * @param response The GenerateResponse
     * @return the text extracted from the list of the Content and appended
     */
    public static String getText(GenerateResponse response) {
        StringBuilder text = new StringBuilder();
        if (response.content() != null && !response.content().isEmpty()) {
            for (GenerateResponse.Content content : response.content()) {
                if (content.type().equals("text")) {
                    text.append(content.text());
                }
            }
        }
        return text.toString();
    }

    /**
     * Extract from the Response the content where the type is tool_use and create for each entry a ToolExecutionRequest
     *
     * @param response the GenerateResponse
     * @return the List of the ToolExecutionRequest
     */
    public static List<ToolExecutionRequest> getToolExecutionRequests(GenerateResponse response) {
        return response.content().stream()
                .filter(c -> "tool_use".equals(c.type()))
                .map(c -> {
                    try {
                        return ToolExecutionRequest.builder()
                                .id(c.id())
                                .name(c.name())
                                .arguments(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValueAsString(c.input()))
                                .build();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to serialize tool arguments", e);
                    }
                })
                .toList();

    }

    /**
     * Extract from the Response the text where the Content type is "thinking"
     *
     * @param response The GenerateResponse
     * @return the text extracted from the list of the Content and appended
     */
    public static String getThoughts(GenerateResponse response) {
        StringBuilder text = new StringBuilder();
        if (response.content() != null && !response.content().isEmpty()) {
            for (GenerateResponse.Content content : response.content()) {
                if (content.type().equals("thinking")) {
                    text.append(content.text());
                }
            }
        }
        return text.toString();
    }
}

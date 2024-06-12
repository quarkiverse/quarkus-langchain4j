package io.quarkiverse.langchain4j.ollama;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;

/**
 * Does not add any tools, default behavior
 */
public class EmptyToolsHandler implements ToolsHandler {
    @Override
    public ChatRequest.Builder enhanceWithTools(ChatRequest.Builder requestBuilder, List<Message> messages,
            List<ToolSpecification> toolSpecifications, ToolSpecification toolThatMustBeExecuted) {
        return requestBuilder;
    }

    @Override
    public AiMessage handleResponse(ChatResponse response, List<ToolSpecification> toolSpecifications) {
        return AiMessage.from(response.message().content());
    }
}

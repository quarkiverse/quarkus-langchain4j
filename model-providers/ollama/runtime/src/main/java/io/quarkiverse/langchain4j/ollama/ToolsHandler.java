package io.quarkiverse.langchain4j.ollama;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;

public interface ToolsHandler {
    /**
     * Transform the request builder following model specificity.
     *
     * @param requestBuilder the original request builder
     * @param messages the list of messages
     * @param toolSpecifications the specifications of tools that could be used
     * @param toolThatMustBeExecuted the toolThatMustBeExecuted
     * @return the modified request builder
     */
    ChatRequest.Builder enhanceWithTools(ChatRequest.Builder requestBuilder, List<Message> messages,
            List<ToolSpecification> toolSpecifications, ToolSpecification toolThatMustBeExecuted);

    /**
     * Enhance the AiResponse with needed tools messages
     *
     * @param response the response from llm to analyse and see if tools should be executed
     * @param toolSpecifications the tools that could be used
     * @return the extended AiMessage
     */
    AiMessage getAiMessageFromResponse(ChatResponse response, List<ToolSpecification> toolSpecifications);
}

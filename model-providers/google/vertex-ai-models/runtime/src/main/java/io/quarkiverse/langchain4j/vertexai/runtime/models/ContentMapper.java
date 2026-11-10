package io.quarkiverse.langchain4j.vertexai.runtime.models;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import java.util.*;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTool;
import dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper;
import dev.langchain4j.model.openai.internal.chat.Role;

/**
 * Generate the Request using the list of the Chat Messages
 */
public class ContentMapper {
    private final static String ANTHROPIC_VERSION = "vertex-2023-10-16";

    /**
     * Generate the Request from the list of the chat messages
     *
     * @param messages the Chat Messages
     * @param max_tokens the number of max tokens to be used
     * @return the GenerateRequest
     */
    public static GenerateRequest map(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            AnthropicThinking thinking,
            Integer max_tokens,
            boolean strict) {

        List<GenerateRequest.Message> requestMessages = new ArrayList<>();

        for (ChatMessage message : messages) {
            switch (message.type()) {
                case SYSTEM -> requestMessages
                        .add(new GenerateRequest.Message(Role.SYSTEM.name().toLowerCase(), chatMessageToText(message)));
                case USER -> requestMessages
                        .add(new GenerateRequest.Message(Role.USER.name().toLowerCase(), chatMessageToText(message)));
                case AI -> requestMessages
                        .add(new GenerateRequest.Message(Role.ASSISTANT.name().toLowerCase(), chatMessageToText(message)));

                // As Anthropic API only accepts as role: user and assistant, then we return user
                // We cannot use as role assistant as we will get as response:
                // This model does not support assistant message prefill. The conversation must end with a user message.
                case TOOL_EXECUTION_RESULT ->
                    requestMessages
                            .add(new GenerateRequest.Message(Role.USER.name().toLowerCase(), chatMessageToText(message)));
                default -> throw new IllegalArgumentException("Unsupported message type: " + message.type());
            }
        }

        return new GenerateRequest(ANTHROPIC_VERSION, max_tokens, requestMessages, toTools(toolSpecifications, strict),
                thinking);
    }

    public static List<AnthropicTool> toTools(List<ToolSpecification> toolSpecifications, boolean strict) {
        return AnthropicMapper.toAnthropicTools(toolSpecifications, AnthropicCacheType.NO_CACHE, strict);
    }
}

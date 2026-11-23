package io.quarkiverse.langchain4j.gemini.common;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

public abstract class GeminiChatLanguageModel extends BaseGeminiChatModel implements ChatModel {

    public GeminiChatLanguageModel(String modelId, Double temperature, Integer maxOutputTokens, Integer topK,
            Double topP, ResponseFormat responseFormat, List<ChatModelListener> listeners, Long thinkingBudget,
            boolean includeThoughts, ThinkingLevel thinkingLevel, boolean useGoogleSearch) {
        super(modelId, temperature, maxOutputTokens, topK, topP, responseFormat, listeners, thinkingBudget,
                includeThoughts, thinkingLevel, useGoogleSearch);
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        Set<Capability> capabilities = new HashSet<>();
        // when response format is not null, it's JSON, either application/json or text/x.enum
        if (this.responseFormat != null && ResponseFormatType.JSON.equals(this.responseFormat.type())) {
            capabilities.add(RESPONSE_FORMAT_JSON_SCHEMA);
        } else if (responseFormat == null) { // when the responseFormat is not set, we default to supporting JSON as Google's models support this
            capabilities.add(RESPONSE_FORMAT_JSON_SCHEMA);
        }
        return capabilities;
    }

    /**
     * Listener notification is left to {@link ChatModel#chat(ChatRequest, dev.langchain4j.model.chat.ChatRequestOptions)},
     * which wraps this method - that is what carries the current invocation id, the per-call listener attributes
     * and the globally registered observability listeners to the {@link ChatModelListener}s.
     */
    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        ChatRequestParameters requestParameters = chatRequest.parameters();
        ResponseFormat effectiveResponseFormat = getOrDefault(requestParameters.responseFormat(), responseFormat);
        Schema schema = detectSchema(effectiveResponseFormat);
        Map<String, Object> rawSchema = detectRawSchema(effectiveResponseFormat);

        GenerationConfig.Builder generationConfigBuilder = GenerationConfig.builder()
                .maxOutputTokens(getOrDefault(requestParameters.maxOutputTokens(), this.maxOutputTokens))
                .responseMimeType(computeMimeType(effectiveResponseFormat))
                .responseSchema(schema)
                .responseJsonSchema(rawSchema)
                .stopSequences(requestParameters.stopSequences())
                .temperature(getOrDefault(requestParameters.temperature(), this.temperature))
                .topK(getOrDefault(requestParameters.topK(), this.topK))
                .topP(getOrDefault(requestParameters.topP(), this.topP));
        applyThinkingConfig(generationConfigBuilder);
        GenerationConfig generationConfig = generationConfigBuilder.build();
        GenerateContentRequest request = ContentMapper.map(chatRequest.messages(), chatRequest.toolSpecifications(),
                requestParameters.toolChoice(),
                generationConfig, this.modelId, this.useGoogleSearch);

        GenerateContentResponse response = generateContext(request);

        String text = GenerateContentResponseHandler.getText(response);
        String thoughts = includeThoughts ? GenerateContentResponseHandler
                .getThoughts(response) : null;
        List<ToolExecutionRequest> toolExecutionRequests = GenerateContentResponseHandler
                .getToolExecutionRequests(response);
        AiMessage aiMessage = AiMessage.builder()
                .text(text)
                .thinking(thoughts)
                .toolExecutionRequests(toolExecutionRequests)
                .build();

        TokenUsage tokenUsage = GenerateContentResponseHandler.getTokenUsage(response.usageMetadata());
        FinishReason finishReason = aiMessage.hasToolExecutionRequests()
                ? FinishReason.TOOL_EXECUTION
                : FinishReasonMapper.map(GenerateContentResponseHandler.getFinishReason(response));

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .modelName(modelId)
                        .tokenUsage(tokenUsage)
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    protected abstract GenerateContentResponse generateContext(GenerateContentRequest request);
}

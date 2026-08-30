package io.quarkiverse.langchain4j.vertexai.runtime.models;

import static dev.langchain4j.internal.Utils.getOrDefault;

import java.time.Duration;
import java.util.*;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

public abstract class VertexAiBaseChatModel implements ChatModel {

    private static final Logger log = Logger.getLogger(VertexAiBaseChatModel.class);

    protected final Double temperature;
    protected final Integer maxOutputTokens;
    protected final Integer topK;
    protected final Double topP;
    protected final Boolean logRequests;
    protected final Boolean logResponses;
    // See: https://platform.claude.com/docs/en/api/messages#tool.strict
    // When true, guarantees schema validation on tool names and inputs
    protected Boolean strict;
    protected final Duration timeout;
    protected final boolean includeThoughts;
    protected Integer thinkingBudgetTokens;
    protected String thinkingType = "enabled";

    public VertexAiBaseChatModel(Double temperature, Integer maxOutputTokens, Integer topK,
            Double topP, Boolean logRequests, Boolean logResponses, Boolean strict, Duration timeout, boolean includeThoughts,
            Integer thinkingBudgetTokens) {
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.topK = topK;
        this.topP = topP;
        this.logRequests = logRequests;
        this.logResponses = logResponses;
        this.strict = strict;
        this.timeout = timeout;
        this.includeThoughts = includeThoughts;
        this.thinkingBudgetTokens = thinkingBudgetTokens;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestParameters requestParameters = chatRequest.parameters();
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
        // TODO: Is such a feature supported ?
        // ResponseFormat effectiveResponseFormat = getOrDefault(requestParameters.responseFormat(), responseFormat);
        // Schema schema = detectSchema(effectiveResponseFormat);
        // Map<String, Object> rawSchema = detectRawSchema(effectiveResponseFormat);

        VertexAiConfig.Builder vertexAiConfigBuilder = VertexAiConfig.builder()
                .maxOutputTokens(getOrDefault(requestParameters.maxOutputTokens(), this.maxOutputTokens))
                //.responseMimeType(computeMimeType(effectiveResponseFormat, schema, rawSchema))
                //.responseSchema(schema)
                //.responseJsonSchema(rawSchema)
                .stopSequences(requestParameters.stopSequences())
                .temperature(getOrDefault(requestParameters.temperature(), this.temperature))
                .topK(getOrDefault(requestParameters.topK(), this.topK))
                .topP(getOrDefault(requestParameters.topP(), this.topP));

        VertexAiConfig vertexAiConfig = vertexAiConfigBuilder.build();

        if (logRequests) {
            log.info("Request: " + chatRequest.messages());
        }

        // Check if thinking is enabled: https://platform.claude.com/docs/en/build-with-claude/extended-thinking
        // TODO: Check the documentation to implement the thinking type: adaptive
        AnthropicThinking anthropicThinking = null;
        if (includeThoughts) {
            if (thinkingType != null || thinkingBudgetTokens != null) {
                anthropicThinking = AnthropicThinking.builder()
                        .type(thinkingType) // TODO:
                        .budgetTokens(thinkingBudgetTokens)
                        .build();
            }
        }

        GenerateRequest request = ContentMapper.map(
                chatRequest.messages(),
                toolSpecifications,
                anthropicThinking,
                this.maxOutputTokens,
                this.strict);
        GenerateResponse response = callApi(request);

        // Let's analyze the response we got to determine what AI is saying
        String aiText = GenerateResponseHandler.getText(response);
        List<ToolExecutionRequest> toolExecutionRequests = GenerateResponseHandler.getToolExecutionRequests(response);

        // If we have enabled thinking, then got the responses from LLM
        String thoughts = includeThoughts ? GenerateResponseHandler
                .getThoughts(response) : null;

        AiMessage.Builder aiMessageBuilder = AiMessage.builder()
                .text(aiText)
                .thinking(thoughts)
                .toolExecutionRequests(toolExecutionRequests);
        AiMessage aiMessage = aiMessageBuilder.build();

        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .finishReason(GenerateResponseHandler.getFinishReason(response))
                .build();

        if (logResponses) {
            log.info("Response: " + chatResponse);
        }

        return chatResponse;
    }

    abstract GenerateResponse callApi(GenerateRequest request);
}

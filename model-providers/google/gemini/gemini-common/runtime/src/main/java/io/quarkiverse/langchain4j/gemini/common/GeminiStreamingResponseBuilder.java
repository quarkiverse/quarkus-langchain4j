package io.quarkiverse.langchain4j.gemini.common;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.gemini.common.GenerateContentResponse.Candidate;
import io.quarkiverse.langchain4j.gemini.common.GenerateContentResponse.Candidate.Part;
import io.quarkiverse.langchain4j.gemini.common.GenerateContentResponse.UsageMetadata;

/**
 * A builder class for constructing streaming responses from Gemini AI model.
 * This class accumulates partial responses and builds a final response.
 */
class GeminiStreamingResponseBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringBuilder contentBuilder;
    private final List<ToolExecutionRequest> functionCalls;

    private final AtomicReference<String> id = new AtomicReference<>();
    private final AtomicReference<String> modelName = new AtomicReference<>();
    private final AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>();
    private final AtomicReference<FinishReason> finishReason = new AtomicReference<>();

    /**
     * Constructs a new GeminiStreamingResponseBuilder.
     */
    public GeminiStreamingResponseBuilder() {
        this.contentBuilder = new StringBuilder();
        this.functionCalls = new ArrayList<>();
    }

    /**
     * Appends a partial response to the builder.
     *
     * @param partialResponse the partial response from Gemini AI
     * @return an Optional containing the text of the partial response, or empty
     *         if no valid text
     */
    public Optional<String> append(GenerateContentResponse partialResponse) {
        if (partialResponse == null) {
            return Optional.empty();
        }

        Candidate firstCandidate = partialResponse.candidates().get(0);

        updateId(partialResponse);
        updateModelName(partialResponse);
        updateFinishReason(firstCandidate);
        updateTokenUsage(partialResponse.usageMetadata());

        Candidate.Content content = firstCandidate.content();
        if (content == null || content.parts() == null) {
            return Optional.empty();
        }

        AiMessage message = fromGPartsToAiMessage(content.parts());
        updateContentAndFunctionCalls(message);

        return Optional.ofNullable(message.text());
    }

    /**
     * Builds the complete response from all accumulated partial responses.
     *
     * @return a Response object containing the complete AiMessage, token usage,
     *         and finish reason
     */
    public ChatResponse build() {
        AiMessage aiMessage = createAiMessage();

        FinishReason finishReason = this.finishReason.get();
        if (aiMessage.hasToolExecutionRequests()) {
            finishReason = TOOL_EXECUTION;
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .id(id.get())
                        .modelName(modelName.get())
                        .tokenUsage(tokenUsage.get())
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private void updateId(GenerateContentResponse response) {
        if (!isNullOrBlank(response.responseId())) {
            id.set(response.responseId());
        }
    }

    private void updateModelName(GenerateContentResponse response) {
        if (!isNullOrBlank(response.modelVersion())) {
            modelName.set(response.modelVersion());
        }
    }

    private void updateTokenUsage(UsageMetadata usageMetadata) {
        if (usageMetadata != null) {
            TokenUsage tokenUsage = new TokenUsage(
                    usageMetadata.promptTokenCount(),
                    usageMetadata.candidatesTokenCount(),
                    usageMetadata.totalTokenCount());
            this.tokenUsage.set(tokenUsage);
        }
    }

    private void updateFinishReason(Candidate candidate) {
        if (candidate.finishReason() != null) {
            this.finishReason.set(fromGFinishReasonToFinishReason(candidate.finishReason()));
        }
    }

    private void updateContentAndFunctionCalls(AiMessage message) {
        Optional.ofNullable(message.text()).ifPresent(contentBuilder::append);
        if (message.hasToolExecutionRequests()) {
            functionCalls.addAll(message.toolExecutionRequests());
        }
    }

    private AiMessage createAiMessage() {
        String text = contentBuilder.toString();
        return AiMessage.builder()
                .text(text.isEmpty() ? null : text)
                .toolExecutionRequests(functionCalls)
                .build();
    }

    static AiMessage fromGPartsToAiMessage(List<Part> parts) {
        StringBuilder fullText = new StringBuilder();
        List<FunctionCall> functionCalls = new ArrayList<>();

        for (Part part : parts) {
            String text = part.text();
            if (text != null && !text.isEmpty()) {
                if (!fullText.isEmpty()) {
                    fullText.append("\n\n");
                }
                fullText.append(text);
            }

            if (part.functionCall() != null) {
                functionCalls.add(part.functionCall());
            }
        }

        if (functionCalls.isEmpty()) {
            return AiMessage.from(fullText.toString());
        } else {
            return AiMessage.from(fromToolExecReqToGFunCall(functionCalls));
        }
    }

    static List<ToolExecutionRequest> fromToolExecReqToGFunCall(List<FunctionCall> functionCalls) {
        return functionCalls.stream()
                .map(functionCall -> {
                    try {
                        return ToolExecutionRequest.builder()
                                .name(functionCall.name())
                                .arguments(MAPPER.writeValueAsString(functionCall.args()))
                                .build();
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    static FinishReason fromGFinishReasonToFinishReason(
            io.quarkiverse.langchain4j.gemini.common.GenerateContentResponse.FinishReason geminiFinishReason) {
        switch (geminiFinishReason) {
            case STOP:
                return FinishReason.STOP;
            case BLOCKLIST:
            case PROHIBITED_CONTENT:
            case RECITATION:
            case SPII:
            case SAFETY:
            case LANGUAGE:
                return FinishReason.CONTENT_FILTER;
            case MAX_TOKENS:
                return FinishReason.LENGTH;
            case MALFORMED_FUNCTION_CALL:
            case FINISH_REASON_UNSPECIFIED:
            case OTHER:
                return FinishReason.OTHER;
            default:
                return FinishReason.OTHER;
        }
    }
}

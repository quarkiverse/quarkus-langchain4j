package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;
import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters.LengthPenalty;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse.Result;

public class WatsonxGenerationModel extends Watsonx
        implements ChatModel {

    private static final String INPUT_TOKEN_COUNT_CONTEXT = "INPUT_TOKEN_COUNT";
    private static final String GENERATED_TOKEN_COUNT_CONTEXT = "GENERATED_TOKEN_COUNT";
    private static final String COMPLETE_MESSAGE_CONTEXT = "COMPLETE_MESSAGE";
    private static final String FINISH_REASON_CONTEXT = "FINISH_REASON";
    private static final String MODEL_ID_CONTEXT = "MODEL_ID";

    private final WatsonxGenerationRequestParameters defaultRequestParameters;
    private final String promptJoiner;

    public WatsonxGenerationModel(Builder builder) {
        super(builder);

        LengthPenalty lengthPenalty = null;
        if (Objects.nonNull(builder.decayFactor) || Objects.nonNull(builder.startIndex)) {
            lengthPenalty = new LengthPenalty(builder.decayFactor, builder.startIndex);
        }

        //
        // The space_id, project_id and separator fields cannot be overwritten by the ChatRequest object.
        //
        this.promptJoiner = builder.promptJoiner;
        this.defaultRequestParameters = WatsonxGenerationRequestParameters.builder()
                .modelName(builder.modelId)
                .decodingMethod(builder.decodingMethod)
                .lengthPenalty(lengthPenalty)
                .minNewTokens(builder.minNewTokens)
                .maxOutputTokens(builder.maxNewTokens)
                .randomSeed(builder.randomSeed)
                .stopSequences(builder.stopSequences)
                .temperature(builder.temperature)
                .timeLimit(builder.timeout)
                .topP(builder.topP)
                .topK(builder.topK)
                .repetitionPenalty(builder.repetitionPenalty)
                .truncateInputTokens(builder.truncateInputTokens)
                .includeStopSequence(builder.includeStopSequence)
                .build();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {

        String modelId = chatRequest.parameters().modelName();
        ChatRequestParameters parameters = chatRequest.parameters();

        validate(parameters);

        TextGenerationResponse response = retryOn(new Callable<TextGenerationResponse>() {
            @Override
            public TextGenerationResponse call() throws Exception {
                TextGenerationRequest request = new TextGenerationRequest(modelId, spaceId, projectId,
                        toInput(chatRequest.messages()), TextGenerationParameters.convert(parameters));
                return client.generation(request, version);
            }
        });

        Result result = response.results().get(0);

        var finishReason = toFinishReason(result.stopReason());
        var tokenUsage = new TokenUsage(
                result.inputTokenCount(),
                result.generatedTokenCount());

        return ChatResponse.builder()
                .aiMessage(AiMessage.from(result.generatedText()))
                .metadata(ChatResponseMetadata.builder()
                        .modelName(response.modelId())
                        .tokenUsage(tokenUsage)
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return super.listeners;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    private void validate(ChatRequestParameters parameters) throws UnsupportedFeatureException {
        if (parameters.frequencyPenalty() != null)
            throw new UnsupportedFeatureException("'frequencyPenalty' parameter is not supported.");

        if (parameters.presencePenalty() != null)
            throw new UnsupportedFeatureException("'presencePenalty' parameter is not supported.");

        if (parameters.toolChoice() != null)
            throw new UnsupportedFeatureException("'toolChoice' parameter is not supported.");

        if (parameters.responseFormat() != null)
            throw new UnsupportedFeatureException("'responseFormat' parameter is not supported.");
    }

    private String toInput(List<ChatMessage> messages) {
        return messages.stream()
                .map(new Function<ChatMessage, String>() {
                    @Override
                    public String apply(ChatMessage chatMessage) {
                        return switch (chatMessage.type()) {
                            case AI -> {
                                AiMessage aiMessage = (AiMessage) chatMessage;
                                yield aiMessage.text();
                            }
                            case SYSTEM -> {
                                SystemMessage systemMessage = (SystemMessage) chatMessage;
                                yield systemMessage.text();
                            }
                            case USER -> {
                                UserMessage userMessage = (UserMessage) chatMessage;
                                if (userMessage.hasSingleText())
                                    yield userMessage.singleText();
                                else
                                    throw new RuntimeException(
                                            "For the generation model, the UserMessage can contain only a single text");
                            }
                            case TOOL_EXECUTION_RESULT ->
                                throw new RuntimeException("The generation model doesn't allow the use of tools");
                            default -> throw new RuntimeException("Unsupported chat message type: " + chatMessage.type());
                        };
                    }
                }).collect(joining(this.promptJoiner));
    }

    private FinishReason toFinishReason(String reason) {
        return switch (reason) {
            case "max_tokens", "token_limit" -> FinishReason.LENGTH;
            case "eos_token", "stop_sequence" -> FinishReason.STOP;
            case "not_finished", "cancelled", "time_limit", "error" -> FinishReason.OTHER;
            default -> throw new IllegalArgumentException("%s not supported".formatted(reason));
        };
    }

    public static final class Builder extends Watsonx.Builder<Builder> {

        private String decodingMethod;
        private Double decayFactor;
        private Integer startIndex;
        private Integer maxNewTokens;
        private Integer minNewTokens;
        private Integer randomSeed;
        private List<String> stopSequences;
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Double repetitionPenalty;
        private Integer truncateInputTokens;
        private Boolean includeStopSequence;
        private String promptJoiner;

        public Builder decodingMethod(String decodingMethod) {
            this.decodingMethod = decodingMethod;
            return this;
        }

        public Builder decayFactor(Double decayFactor) {
            this.decayFactor = decayFactor;
            return this;
        }

        public Builder startIndex(Integer startIndex) {
            this.startIndex = startIndex;
            return this;
        }

        public Builder minNewTokens(Integer minNewTokens) {
            this.minNewTokens = minNewTokens;
            return this;
        }

        public Builder maxNewTokens(Integer maxNewTokens) {
            this.maxNewTokens = maxNewTokens;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder repetitionPenalty(Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder truncateInputTokens(Integer truncateInputTokens) {
            this.truncateInputTokens = truncateInputTokens;
            return this;
        }

        public Builder includeStopSequence(Boolean includeStopSequence) {
            this.includeStopSequence = includeStopSequence;
            return this;
        }

        public Builder promptJoiner(String promptJoiner) {
            this.promptJoiner = promptJoiner;
            return this;
        }

        public WatsonxGenerationModel build() {
            return new WatsonxGenerationModel(this);
        }
    }
}

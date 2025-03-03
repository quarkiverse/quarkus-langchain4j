package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;
import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters.LengthPenalty;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse.Result;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.smallrye.mutiny.Context;

public class WatsonxGenerationModel extends Watsonx
        implements ChatLanguageModel, StreamingChatLanguageModel, TokenCountEstimator {

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

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        String modelId = chatRequest.parameters().modelName();
        ChatRequestParameters parameters = chatRequest.parameters();

        validate(parameters);

        TextGenerationRequest request = new TextGenerationRequest(modelId, spaceId, projectId, toInput(chatRequest.messages()),
                TextGenerationParameters.convert(parameters));

        Context context = Context.empty();
        context.put(COMPLETE_MESSAGE_CONTEXT, new StringBuilder());
        context.put(INPUT_TOKEN_COUNT_CONTEXT, 0);
        context.put(GENERATED_TOKEN_COUNT_CONTEXT, 0);

        client.generationStreaming(request, version)
                .onFailure(WatsonxUtils::isTokenExpired).retry().atMost(1)
                .subscribe()
                .with(context,
                        new Consumer<TextGenerationResponse>() {
                            @Override
                            public void accept(TextGenerationResponse response) {
                                try {

                                    if (response == null || response.results() == null || response.results().isEmpty())
                                        return;

                                    StringBuilder stringBuilder = context.get(COMPLETE_MESSAGE_CONTEXT);
                                    Result chunk = response.results().get(0);

                                    if (!context.contains(MODEL_ID_CONTEXT) && response.modelId() != null) {
                                        context.put(MODEL_ID_CONTEXT, response.modelId());
                                    }

                                    if (!chunk.stopReason().equals("not_finished")) {
                                        context.put(FINISH_REASON_CONTEXT, chunk.stopReason());
                                    }

                                    int inputTokenCount = context.get(INPUT_TOKEN_COUNT_CONTEXT);
                                    context.put(INPUT_TOKEN_COUNT_CONTEXT, inputTokenCount + chunk.inputTokenCount());

                                    int generatedTokenCount = context.get(GENERATED_TOKEN_COUNT_CONTEXT);
                                    context.put(GENERATED_TOKEN_COUNT_CONTEXT,
                                            generatedTokenCount + chunk.generatedTokenCount());

                                    stringBuilder.append(chunk.generatedText());
                                    handler.onPartialResponse(chunk.generatedText());

                                } catch (Exception e) {
                                    handler.onError(e);
                                }
                            }
                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable error) {
                                handler.onError(error);
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {

                                StringBuilder response = context.get(COMPLETE_MESSAGE_CONTEXT);
                                FinishReason finishReason = context.contains(FINISH_REASON_CONTEXT)
                                        ? toFinishReason(context.get(FINISH_REASON_CONTEXT))
                                        : null;
                                int inputTokenCount = context.contains(INPUT_TOKEN_COUNT_CONTEXT)
                                        ? context.get(INPUT_TOKEN_COUNT_CONTEXT)
                                        : 0;
                                int outputTokenCount = context.contains(GENERATED_TOKEN_COUNT_CONTEXT)
                                        ? context.get(GENERATED_TOKEN_COUNT_CONTEXT)
                                        : 0;
                                String modelId = context.contains(MODEL_ID_CONTEXT)
                                        ? context.get(MODEL_ID_CONTEXT)
                                        : null;

                                AiMessage aiMessage = AiMessage.from(response.toString());
                                TokenUsage tokenUsage = new TokenUsage(inputTokenCount, outputTokenCount);

                                ChatResponse chatResponse = ChatResponse.builder()
                                        .aiMessage(aiMessage)
                                        .metadata(ChatResponseMetadata.builder()
                                                .modelName(modelId)
                                                .tokenUsage(tokenUsage)
                                                .finishReason(finishReason)
                                                .build())
                                        .build();

                                handler.onCompleteResponse(chatResponse);
                            }
                        });

    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        var chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(defaultRequestParameters())
                .build();
        chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onNext(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                handler.onComplete(Response.from(completeResponse.aiMessage(), completeResponse.tokenUsage(),
                        completeResponse.finishReason()));
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        });
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        var chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(defaultRequestParameters())
                .build();

        var chatResponse = chat(chatRequest);
        return Response.from(chatResponse.aiMessage(), chatResponse.tokenUsage(), chatResponse.finishReason());
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        var input = toInput(messages);
        var request = new TokenizationRequest(modelId, input, spaceId, projectId);

        return retryOn(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return client.tokenization(request, version).result().tokenCount();
            }
        });
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

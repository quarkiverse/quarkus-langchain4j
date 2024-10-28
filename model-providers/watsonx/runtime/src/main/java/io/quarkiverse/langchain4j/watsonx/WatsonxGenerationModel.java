package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
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

    private final TextGenerationParameters parameters;
    private final String promptJoiner;

    public WatsonxGenerationModel(Builder builder) {
        super(builder);

        this.promptJoiner = builder.promptJoiner;

        LengthPenalty lengthPenalty = null;
        if (Objects.nonNull(builder.decayFactor) || Objects.nonNull(builder.startIndex)) {
            lengthPenalty = new LengthPenalty(builder.decayFactor, builder.startIndex);
        }

        this.parameters = TextGenerationParameters.builder()
                .decodingMethod(builder.decodingMethod)
                .lengthPenalty(lengthPenalty)
                .minNewTokens(builder.minNewTokens)
                .maxNewTokens(builder.maxNewTokens)
                .randomSeed(builder.randomSeed)
                .stopSequences(builder.stopSequences)
                .temperature(builder.temperature)
                .timeLimit(builder.timeout.toMillis())
                .topP(builder.topP)
                .topK(builder.topK)
                .repetitionPenalty(builder.repetitionPenalty)
                .truncateInputTokens(builder.truncateInputTokens)
                .includeStopSequence(builder.includeStopSequence)
                .build();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        Result result = retryOn(new Callable<TextGenerationResponse>() {
            @Override
            public TextGenerationResponse call() throws Exception {
                TextGenerationRequest request = new TextGenerationRequest(modelId, spaceId, projectId, toInput(messages),
                        parameters);
                return client.generation(request, version);
            }
        }).results().get(0);

        var finishReason = toFinishReason(result.stopReason());
        var tokenUsage = new TokenUsage(
                result.inputTokenCount(),
                result.generatedTokenCount());

        AiMessage content = AiMessage.from(result.generatedText());
        return Response.from(content, tokenUsage, finishReason);
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        TextGenerationRequest request = new TextGenerationRequest(modelId, spaceId, projectId, toInput(messages), parameters);

        Context context = Context.empty();
        context.put("response", new ArrayList<TextGenerationResponse>());

        client.generationStreaming(request, version)
                .subscribe()
                .with(context,
                        new Consumer<TextGenerationResponse>() {
                            @Override
                            public void accept(TextGenerationResponse response) {
                                try {

                                    if (response == null || response.results() == null || response.results().isEmpty())
                                        return;

                                    String chunk = response.results().get(0).generatedText();

                                    if (chunk.isEmpty())
                                        return;

                                    List<TextGenerationResponse> responses = context.get("response");
                                    responses.add(response);
                                    handler.onNext(chunk);

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
                                List<TextGenerationResponse> list = context.get("response");

                                int inputTokenCount = 0;
                                int outputTokenCount = 0;
                                String stopReason = null;
                                StringBuilder builder = new StringBuilder();

                                for (int i = 0; i < list.size(); i++) {

                                    TextGenerationResponse.Result response = list.get(i).results().get(0);

                                    if (i == 0)
                                        inputTokenCount = response.inputTokenCount();

                                    if (i == list.size() - 1) {
                                        outputTokenCount = response.generatedTokenCount();
                                        stopReason = response.stopReason();
                                    }

                                    builder.append(response.generatedText());
                                }

                                AiMessage content;
                                TokenUsage tokenUsage = new TokenUsage(inputTokenCount, outputTokenCount);
                                FinishReason finishReason = toFinishReason(stopReason);

                                String message = builder.toString();
                                content = AiMessage.from(message);
                                handler.onComplete(Response.from(content, tokenUsage, finishReason));
                            }
                        });
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

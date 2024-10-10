package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters.LengthPenalty;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse.Result;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Context;

public class WatsonxGenerationModel implements ChatLanguageModel, StreamingChatLanguageModel, TokenCountEstimator {

    private static final Logger log = Logger.getLogger(WatsonxGenerationModel.class);

    private final String modelId, projectId, version;
    private final WatsonxRestApi client;
    private final Parameters parameters;
    private final PromptFormatter promptFormatter;

    public WatsonxGenerationModel(Builder builder) {

        QuarkusRestClientBuilder restClientBuilder = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(builder.url)
                .clientHeadersFactory(new BearerTokenHeaderFactory(builder.tokenGenerator))
                .connectTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS);

        if (builder.logRequests || builder.logResponses) {
            restClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            restClientBuilder.clientLogger(new WatsonxRestApi.WatsonClientLogger(
                    builder.logRequests,
                    builder.logResponses));
        }

        this.client = restClientBuilder.build(WatsonxRestApi.class);
        this.modelId = builder.modelId;
        this.projectId = builder.projectId;
        this.version = builder.version;

        if (builder.promptFormatter != null) {
            this.promptFormatter = builder.promptFormatter;
        } else {
            this.promptFormatter = null;
        }

        LengthPenalty lengthPenalty = null;
        if (Objects.nonNull(builder.decayFactor) || Objects.nonNull(builder.startIndex)) {
            lengthPenalty = new LengthPenalty(builder.decayFactor, builder.startIndex);
        }

        this.parameters = Parameters.builder()
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
        TextGenerationRequest request = new TextGenerationRequest(modelId, projectId, toInput(messages), parameters);

        Result result = retryOn(new Callable<TextGenerationResponse>() {
            @Override
            public TextGenerationResponse call() throws Exception {
                return client.chat(request, version);
            }
        }).results().get(0);

        var finishReason = toFinishReason(result.stopReason());
        var content = AiMessage.from(result.generatedText());
        var tokenUsage = new TokenUsage(
                result.inputTokenCount(),
                result.generatedTokenCount());

        return Response.from(content, tokenUsage, finishReason);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        TextGenerationRequest request = new TextGenerationRequest(modelId, projectId, toInput(messages, toolSpecifications),
                parameters);

        Result result = retryOn(new Callable<TextGenerationResponse>() {
            @Override
            public TextGenerationResponse call() throws Exception {
                return client.chat(request, version);
            }
        }).results().get(0);

        var finishReason = toFinishReason(result.stopReason());
        var tokenUsage = new TokenUsage(
                result.inputTokenCount(),
                result.generatedTokenCount());

        AiMessage content;

        if (result.generatedText().startsWith(promptFormatter.toolExecution())) {
            var tools = result.generatedText().replace(promptFormatter.toolExecution(), "");
            content = AiMessage.from(promptFormatter.toolExecutionRequestFormatter(tools));
        } else {
            content = AiMessage.from(result.generatedText());
        }

        return Response.from(content, tokenUsage, finishReason);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, List.of(toolSpecification));
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        TextGenerationRequest request = new TextGenerationRequest(modelId, projectId, toInput(messages), parameters);
        Context context = Context.of("response", new ArrayList<TextGenerationResponse>());

        client.chatStreaming(request, version)
                .subscribe()
                .with(context,
                        new Consumer<TextGenerationResponse>() {
                            @Override
                            @SuppressWarnings("unchecked")
                            public void accept(TextGenerationResponse response) {
                                try {

                                    if (response == null || response.results() == null || response.results().isEmpty())
                                        return;

                                    ((List<TextGenerationResponse>) context.get("response")).add(response);
                                    handler.onNext(response.results().get(0).generatedText());

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
                            @SuppressWarnings("unchecked")
                            public void run() {
                                var list = ((List<TextGenerationResponse>) context.get("response"));

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

                                AiMessage message = new AiMessage(builder.toString());
                                TokenUsage tokenUsage = new TokenUsage(inputTokenCount, outputTokenCount);
                                FinishReason finishReason = toFinishReason(stopReason);
                                handler.onComplete(Response.from(message, tokenUsage, finishReason));
                            }
                        });
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {

        var input = toInput(messages);
        var request = new TokenizationRequest(modelId, input, projectId);

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
        var prompt = promptFormatter.format(messages, List.of());
        log.debugf("""
                Formatted prompt:
                -----------------
                %s
                -----------------""", prompt);
        return prompt;
    }

    private String toInput(List<ChatMessage> messages, List<ToolSpecification> tools) {
        var prompt = promptFormatter.format(messages, tools);
        log.debugf("""
                Formatted prompt:
                -----------------
                %s
                -----------------""", prompt);
        return prompt;
    }

    private FinishReason toFinishReason(String stopReason) {
        return switch (stopReason) {
            case "max_tokens", "token_limit" -> FinishReason.LENGTH;
            case "eos_token", "stop_sequence" -> FinishReason.STOP;
            case "not_finished", "cancelled", "time_limit", "error" -> FinishReason.OTHER;
            default -> throw new IllegalArgumentException("%s not supported".formatted(stopReason));
        };
    }

    public static final class Builder {

        private String modelId;
        private String version;
        private String projectId;
        private Duration timeout;
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
        private URL url;
        public boolean logResponses;
        public boolean logRequests;
        private WatsonxTokenGenerator tokenGenerator;
        private PromptFormatter promptFormatter;

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

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

        public Builder tokenGenerator(WatsonxTokenGenerator tokenGenerator) {
            this.tokenGenerator = tokenGenerator;
            return this;
        }

        public Builder promptFormatter(PromptFormatter promptFormatter) {
            this.promptFormatter = promptFormatter;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public WatsonxGenerationModel build() {
            return new WatsonxGenerationModel(this);
        }
    }
}

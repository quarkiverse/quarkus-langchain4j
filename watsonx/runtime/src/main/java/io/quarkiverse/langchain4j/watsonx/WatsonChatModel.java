package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.Utility.retryOn;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse.Result;
import io.quarkiverse.langchain4j.watsonx.client.WatsonRestApi;
import io.quarkiverse.langchain4j.watsonx.client.filter.BearerRequestFilter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class WatsonChatModel implements ChatLanguageModel {

    private final String modelId;
    private final String version;
    private final String projectId;
    private final String decodingMethod;
    private final Integer minNewTokens;
    private final Integer maxNewTokens;
    private Integer randomSeed;
    private List<String> stopSequences;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private Double repetitionPenalty;
    private final WatsonRestApi client;

    public WatsonChatModel(Builder config) {

        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(config.url)
                .connectTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS);

        if (config.logRequests || config.logResponses) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new WatsonRestApi.WatsonClientLogger(
                    config.logRequests,
                    config.logResponses));
        }

        if (config.tokenGenerator != null) {
            builder.register(new BearerRequestFilter(config.tokenGenerator));
        }

        this.client = builder.build(WatsonRestApi.class);
        this.modelId = config.modelId;
        this.version = config.version;
        this.projectId = config.projectId;
        this.decodingMethod = config.decodingMethod;
        this.minNewTokens = config.minNewTokens;
        this.maxNewTokens = config.maxNewTokens;
        this.randomSeed = config.randomSeed;
        this.stopSequences = config.stopSequences;
        this.temperature = config.temperature;
        this.topP = config.topP;
        this.topK = config.topK;
        this.repetitionPenalty = config.repetitionPenalty;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        Parameters parameters = Parameters.builder()
                .decodingMethod(decodingMethod)
                .minNewTokens(minNewTokens)
                .maxNewTokens(maxNewTokens)
                .randomSeed(randomSeed)
                .stopSequences(stopSequences)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .repetitionPenalty(repetitionPenalty)
                .build();

        TextGenerationRequest request = new TextGenerationRequest(modelId, projectId, toInput(messages), parameters);

        // The response for will be always one.
        Result result = retryOn(() -> client.chat(request, version)).results().get(0);

        var finishReason = toFinishReason(result.stopReason());
        var content = AiMessage.from(result.generatedText());
        var tokenUsage = new TokenUsage(
                result.inputTokenCount(),
                result.generatedTokenCount());

        return Response.from(content, tokenUsage, finishReason);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported for Watsonx models");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new IllegalArgumentException("Tools are currently not supported for Watsonx models");
    }

    private String toInput(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            switch (message.type()) {
                case AI:
                case USER:
                    builder.append(message.text())
                            .append("\n");
                    break;
                case SYSTEM:
                    builder.append(message.text())
                            .append("\n\n");
                    break;
                case TOOL_EXECUTION_RESULT:
                    throw new IllegalArgumentException("Tool message is not supported");
            }
        }
        return builder.toString();
    }

    private FinishReason toFinishReason(String stopReason) {
        switch (stopReason) {
            case "max_tokens":
                return FinishReason.LENGTH;
            case "eos_token":
            case "stop_sequence":
                return FinishReason.STOP;
            default:
                throw new IllegalArgumentException("%s not supported".formatted(stopReason));
        }
    }

    public static final class Builder {

        private String modelId;
        private String version;
        private String projectId;
        private Duration timeout;
        private String decodingMethod;
        private Integer minNewTokens;
        private Integer maxNewTokens;
        private Integer randomSeed;
        private List<String> stopSequences;
        private Double temperature;
        private URL url;
        private Integer topK;
        private Double topP;
        private Double repetitionPenalty;
        public boolean logResponses;
        public boolean logRequests;
        private TokenGenerator tokenGenerator;

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

        public Builder decondingMethod(String decodingMethod) {
            this.decodingMethod = decodingMethod;
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

        public Builder tokenGenerator(TokenGenerator tokenGenerator) {
            this.tokenGenerator = tokenGenerator;
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

        public WatsonChatModel build() {
            return new WatsonChatModel(this);
        }
    }
}

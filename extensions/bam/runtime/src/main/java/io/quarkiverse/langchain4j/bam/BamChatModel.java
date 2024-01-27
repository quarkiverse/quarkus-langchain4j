package io.quarkiverse.langchain4j.bam;

import static java.util.stream.Collectors.joining;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class BamChatModel implements ChatLanguageModel, TokenCountEstimator {

    private final String token;
    private final String modelId;
    private final String version;
    private final String decodingMethod;
    private Boolean includeStopSequence;
    private final Integer minNewTokens;
    private final Integer maxNewTokens;
    private Integer randomSeed;
    private List<String> stopSequences;
    private final Double temperature;
    private Integer timeLimit;
    private final Double topP;
    private final Integer topK;
    private Double typicalP;
    private Double repetitionPenalty;
    private Integer truncateInputTokens;
    private Integer beamWidth;
    private final BamRestApi client;

    public BamChatModel(Builder config) {

        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(config.url)
                .connectTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS);

        if (config.logRequests || config.logResponses) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new BamRestApi.WatsonClientLogger(config.logRequests,
                    config.logResponses));
        }

        this.client = builder.build(BamRestApi.class);
        this.token = config.accessToken;
        this.modelId = config.modelId;
        this.version = config.version;
        this.decodingMethod = config.decodingMethod;
        this.includeStopSequence = config.includeStopSequence;
        this.minNewTokens = config.minNewTokens;
        this.maxNewTokens = config.maxNewTokens;
        this.randomSeed = config.randomSeed;
        this.stopSequences = config.stopSequences;
        this.temperature = config.temperature;
        this.timeLimit = config.timeLimit;
        this.topP = config.topP;
        this.topK = config.topK;
        this.typicalP = config.typicalP;
        this.repetitionPenalty = config.repetitionPenalty;
        this.truncateInputTokens = config.truncateInputTokens;
        this.beamWidth = config.beamWidth;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        Parameters parameters = Parameters.builder()
                .decodingMethod(decodingMethod)
                .includeStopSequence(includeStopSequence)
                .minNewTokens(minNewTokens)
                .maxNewTokens(maxNewTokens)
                .randomSeed(randomSeed)
                .stopSequences(stopSequences)
                .temperature(temperature)
                .timeLimit(timeLimit)
                .topP(topP)
                .topK(topK)
                .typicalP(typicalP)
                .repetitionPenalty(repetitionPenalty)
                .truncateInputTokens(truncateInputTokens)
                .beamWidth(beamWidth)
                .build();

        TextGenerationRequest request = new TextGenerationRequest(modelId,
                messages.stream().map(cm -> new Message(getRole(cm), cm.text())).toList(), parameters);

        TextGenerationResponse textGenerationResponse = client.chat(request, token, version);

        return Response.from(AiMessage.from(textGenerationResponse.results().get(0).generatedText()));
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {

        var input = messages
                .stream()
                .map(ChatMessage::text)
                .collect(joining("\n"));

        var request = new TokenizationRequest(modelId, input);
        return client.tokenization(request, token, version).tokenCount();
    }

    private String getRole(ChatMessage chatMessage) {
        if (chatMessage instanceof SystemMessage) {
            return "system";
        } else if (chatMessage instanceof UserMessage) {
            return "user";
        } else if (chatMessage instanceof AiMessage) {
            return "assistant";
        }
        throw new IllegalArgumentException(chatMessage.getClass().getSimpleName() + " not supported");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported for BAM models");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new IllegalArgumentException("Tools are currently not supported for BAM models");
    }

    public static final class Builder {

        private String accessToken;
        private String modelId;
        private String version;
        private Duration timeout = Duration.ofSeconds(15);
        private String decodingMethod = "greedy";
        private Boolean includeStopSequence;
        private Integer minNewTokens = 0;
        private Integer maxNewTokens = 200;
        private Integer randomSeed;
        private List<String> stopSequences;
        private Double temperature;
        private Integer timeLimit;
        private URI url = URI.create("https://bam-api.res.ibm.com");
        private Integer topK;
        private Double topP;
        private Double typicalP;
        private Double repetitionPenalty;
        private Integer truncateInputTokens;
        private Integer beamWidth;
        public boolean logResponses;
        public boolean logRequests;

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder url(URL url) {
            try {
                this.url = url.toURI();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
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

        public Builder includeStopSequence(Boolean includeStopSequence) {
            this.includeStopSequence = includeStopSequence;
            return this;
        }

        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder typicalP(Double typicalP) {
            this.typicalP = typicalP;
            return this;
        }

        public Builder repetitionPenalty(Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public Builder truncateInputTokens(Integer truncateInputTokens) {
            this.truncateInputTokens = truncateInputTokens;
            return this;
        }

        public Builder beamWidth(Integer beamWidth) {
            this.beamWidth = beamWidth;
            return this;
        }

        public Builder timeLimit(Integer timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public BamChatModel build() {
            return new BamChatModel(this);
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }
    }
}

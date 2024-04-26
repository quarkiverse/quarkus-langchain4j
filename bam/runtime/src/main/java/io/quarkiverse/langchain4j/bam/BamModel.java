package io.quarkiverse.langchain4j.bam;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.output.FinishReason;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public abstract class BamModel {

    final String token;
    final String modelId;
    final String version;
    final String decodingMethod;
    final Boolean includeStopSequence;
    final Integer minNewTokens;
    final Integer maxNewTokens;
    final Integer randomSeed;
    final List<String> stopSequences;
    final Double temperature;
    final Integer timeLimit;
    final Double topP;
    final Integer topK;
    final Double typicalP;
    final Double repetitionPenalty;
    final Integer truncateInputTokens;
    final Integer beamWidth;
    final Set<ChatMessageType> messagesToModerate;
    final Float hap;
    final Float socialBias;
    final BamRestApi client;

    public BamModel(Builder config) {

        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(config.url)
                .connectTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS);

        if (config.logRequests || config.logResponses) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new BamRestApi.BamClientLogger(config.logRequests,
                    config.logResponses));
        }

        if (config.messagesToModerate != null && config.messagesToModerate.size() > 0) {
            this.messagesToModerate = Set.copyOf(config.messagesToModerate);
        } else {
            this.messagesToModerate = null;
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
        this.hap = config.hap;
        this.socialBias = config.socialBias;
    }

    public static Builder builder() {
        return new Builder();
    }

    protected List<Message> toInput(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            switch (message.type()) {
                case AI:
                    result.add(new Message("assistant", message.text()));
                    break;
                case USER:
                    result.add(new Message("user", message.text()));
                    break;
                case SYSTEM:
                    result.add(new Message("system", message.text()));
                    break;
                case TOOL_EXECUTION_RESULT:
                    throw new IllegalArgumentException("Tool message is not supported");
            }
        }
        return result;
    }

    protected FinishReason toFinishReason(String stopReason) {
        return switch (stopReason) {
            case "max_tokens" -> FinishReason.LENGTH;
            case "eos_token", "stop_sequence" -> FinishReason.STOP;
            default -> throw new IllegalArgumentException("%s not supported".formatted(stopReason));
        };
    }

    public static final class Builder {

        private String accessToken;
        private String modelId;
        private String version;
        private Duration timeout;
        private String decodingMethod;
        private Boolean includeStopSequence;
        private Integer minNewTokens;
        private Integer maxNewTokens;
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
        private List<ChatMessageType> messagesToModerate;
        private Float hap;
        private Float socialBias;
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

        public Builder messagesToModerate(List<ChatMessageType> messagesToModerate) {
            this.messagesToModerate = messagesToModerate;
            return this;
        }

        public Builder hap(Float hap) {
            this.hap = hap;
            return this;
        }

        public Builder socialBias(Float socialBias) {
            this.socialBias = socialBias;
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

        public <T> T build(Class<T> clazz) {
            try {
                return clazz.getConstructor(Builder.class).newInstance(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

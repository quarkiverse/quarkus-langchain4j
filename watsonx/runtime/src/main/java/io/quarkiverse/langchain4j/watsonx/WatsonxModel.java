package io.quarkiverse.langchain4j.watsonx;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.WebApplicationException;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.watsonx.bean.WatsonxError;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonxException;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Uni;

public abstract class WatsonxModel {

    private final TokenGenerator tokenGenerator;
    final String modelId;
    final String version;
    final String projectId;
    final String decodingMethod;
    final Integer minNewTokens;
    final Integer maxNewTokens;
    final Integer randomSeed;
    final List<String> stopSequences;
    final Double temperature;
    final Double topP;
    final Integer topK;
    final Double repetitionPenalty;
    final WatsonxRestApi client;

    public WatsonxModel(Builder config) {

        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(config.url)
                .connectTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS);

        if (config.logRequests || config.logResponses) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new WatsonxRestApi.WatsonClientLogger(
                    config.logRequests,
                    config.logResponses));
        }

        this.client = builder.build(WatsonxRestApi.class);
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
        this.tokenGenerator = config.tokenGenerator;
    }

    public static Builder builder() {
        return new Builder();
    }

    protected Uni<String> generateBearerToken() {
        return tokenGenerator.generate();
    }

    protected String toInput(List<ChatMessage> messages) {
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

    protected FinishReason toFinishReason(String stopReason) {
        return switch (stopReason) {
            case "max_tokens" -> FinishReason.LENGTH;
            case "eos_token", "stop_sequence" -> FinishReason.STOP;
            default -> throw new IllegalArgumentException("%s not supported".formatted(stopReason));
        };
    }

    protected static <T> T retryOn(Callable<T> action) {

        int maxAttempts = 1;
        for (int i = 0; i <= maxAttempts; i++) {

            try {

                return action.call();

            } catch (WatsonxException e) {

                if (e.details() == null || e.details().errors() == null || e.details().errors().size() == 0)
                    throw e;

                Optional<WatsonxError.Code> optional = Optional.empty();
                for (WatsonxError.Error error : e.details().errors()) {
                    if (WatsonxError.Code.AUTHENTICATION_TOKEN_EXPIRED.equals(error.code())) {
                        optional = Optional.of(error.code());
                        break;
                    }
                }

                if (!optional.isPresent())
                    throw e;

            } catch (WebApplicationException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Failed after " + maxAttempts + " attempts");
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

        public <T> T build(Class<T> clazz) {
            try {
                return clazz.getConstructor(Builder.class).newInstance(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

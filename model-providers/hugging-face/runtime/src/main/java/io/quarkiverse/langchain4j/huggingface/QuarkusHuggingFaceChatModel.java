package io.quarkiverse.langchain4j.huggingface;

import static java.util.stream.Collectors.joining;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.client.Options;
import dev.langchain4j.model.huggingface.client.Parameters;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import io.quarkiverse.langchain4j.huggingface.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.runtime.LangChain4jUtil;

/**
 * This is a Quarkus specific version of the HuggingFace model.
 * <p>
 * TODO: remove this in the future when the stock {@link dev.langchain4j.model.huggingface.HuggingFaceChatModel}
 * has been updated to fit our needs (i.e. allowing {@code returnFullText} to be null and making {code accessToken} optional)
 */
public class QuarkusHuggingFaceChatModel implements ChatModel {

    public static final QuarkusHuggingFaceClientFactory CLIENT_FACTORY = new QuarkusHuggingFaceClientFactory();
    private final HuggingFaceClient client;
    private final Double temperature;
    private final Integer maxNewTokens;
    private final Boolean returnFullText;
    private final Boolean waitForModel;
    private final Optional<Boolean> doSample;
    private final OptionalDouble topP;
    private final OptionalInt topK;
    private final OptionalDouble repetitionPenalty;

    private QuarkusHuggingFaceChatModel(Builder builder) {
        this.client = CLIENT_FACTORY.create(builder, new HuggingFaceClientFactory.Input() {
            @Override
            public String apiKey() {
                return builder.accessToken;
            }

            @Override
            public String modelId() {
                throw new UnsupportedOperationException("Should not be called");
            }

            @Override
            public Duration timeout() {
                return builder.timeout;
            }
        }, builder.url);
        this.temperature = builder.temperature;
        this.maxNewTokens = builder.maxNewTokens;
        this.returnFullText = builder.returnFullText;
        this.waitForModel = builder.waitForModel;
        this.doSample = builder.doSample;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.repetitionPenalty = builder.repetitionPenalty;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {

        Parameters.Builder builder = Parameters.builder()
                .temperature(temperature)
                .maxNewTokens(maxNewTokens)
                .returnFullText(returnFullText);

        doSample.ifPresent(builder::doSample);
        topK.ifPresent(builder::topK);
        topP.ifPresent(builder::topP);
        repetitionPenalty.ifPresent(builder::repetitionPenalty);

        Parameters parameters = builder
                .build();
        TextGenerationRequest request = TextGenerationRequest.builder()
                .inputs(chatRequest.messages().stream()
                        .map(LangChain4jUtil::chatMessageToText)
                        .collect(joining("\n")))
                .parameters(parameters)
                .options(Options.builder()
                        .waitForModel(waitForModel)
                        .build())
                .build();

        TextGenerationResponse textGenerationResponse = client.chat(request);

        return ChatResponse.builder().aiMessage(AiMessage.from(textGenerationResponse.getGeneratedText())).build();
    }

    public static final class Builder {

        private String accessToken;
        private Duration timeout = Duration.ofSeconds(15);
        private Double temperature;
        private Integer maxNewTokens;
        private Boolean returnFullText;
        private Boolean waitForModel = true;
        private URI url = URI.create(ChatModelConfig.DEFAULT_INFERENCE_ENDPOINT);
        private Optional<Boolean> doSample;

        private OptionalInt topK;
        private OptionalDouble topP;

        private OptionalDouble repetitionPenalty;
        public boolean logResponses;
        public boolean logRequests;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
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

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxNewTokens(Integer maxNewTokens) {
            this.maxNewTokens = maxNewTokens;
            return this;
        }

        public Builder returnFullText(Boolean returnFullText) {
            this.returnFullText = returnFullText;
            return this;
        }

        public Builder waitForModel(Boolean waitForModel) {
            this.waitForModel = waitForModel;
            return this;
        }

        public Builder doSample(Optional<Boolean> doSample) {
            this.doSample = doSample;
            return this;
        }

        public Builder topK(OptionalInt topK) {
            this.topK = topK;
            return this;
        }

        public Builder topP(OptionalDouble topP) {
            this.topP = topP;
            return this;
        }

        public Builder repetitionPenalty(OptionalDouble repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public QuarkusHuggingFaceChatModel build() {
            return new QuarkusHuggingFaceChatModel(this);
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

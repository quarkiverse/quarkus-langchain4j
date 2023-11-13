package io.quarkiverse.langchain4j.huggingface;

import static dev.langchain4j.model.huggingface.HuggingFaceModelName.TII_UAE_FALCON_7B_INSTRUCT;
import static java.util.stream.Collectors.joining;

import java.time.Duration;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.client.Options;
import dev.langchain4j.model.huggingface.client.Parameters;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import dev.langchain4j.model.output.Response;

/**
 * This is a Quarkus specific version of the HuggingFace model.
 * <p>
 * TODO: remove this in the future when the stock {@link dev.langchain4j.model.huggingface.HuggingFaceChatModel}
 * has been updated to fit our needs (i.e. allowing {@code returnFullText} to be null and making {code accessToken} optional)
 */
public class QuarkusHuggingFaceChatModel implements ChatLanguageModel {

    public static final HuggingFaceClientFactory CLIENT_FACTORY = new QuarkusHuggingFaceClientFactory();
    private final HuggingFaceClient client;
    private final Double temperature;
    private final Integer maxNewTokens;
    private final Boolean returnFullText;
    private final Boolean waitForModel;

    private QuarkusHuggingFaceChatModel(Builder builder) {
        this.client = CLIENT_FACTORY.create(new HuggingFaceClientFactory.Input() {
            @Override
            public String apiKey() {
                return builder.accessToken;
            }

            @Override
            public String modelId() {
                return builder.modelId;
            }

            @Override
            public Duration timeout() {
                return builder.timeout;
            }
        });
        this.temperature = builder.temperature;
        this.maxNewTokens = builder.maxNewTokens;
        this.returnFullText = builder.returnFullText;
        this.waitForModel = builder.waitForModel;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        TextGenerationRequest request = TextGenerationRequest.builder()
                .inputs(messages.stream()
                        .map(ChatMessage::text)
                        .collect(joining("\n")))
                .parameters(Parameters.builder()
                        .temperature(temperature)
                        .maxNewTokens(maxNewTokens)
                        .returnFullText(returnFullText)
                        .build())
                .options(Options.builder()
                        .waitForModel(waitForModel)
                        .build())
                .build();

        TextGenerationResponse textGenerationResponse = client.chat(request);

        return Response.from(AiMessage.from(textGenerationResponse.generatedText()));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported for HuggingFace models");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new IllegalArgumentException("Tools are currently not supported for HuggingFace models");
    }

    public static final class Builder {

        private String accessToken;
        private String modelId = TII_UAE_FALCON_7B_INSTRUCT;
        private Duration timeout = Duration.ofSeconds(15);
        private Double temperature;
        private Integer maxNewTokens;
        private Boolean returnFullText;
        private Boolean waitForModel = true;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
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

        public QuarkusHuggingFaceChatModel build() {
            return new QuarkusHuggingFaceChatModel(this);
        }
    }
}

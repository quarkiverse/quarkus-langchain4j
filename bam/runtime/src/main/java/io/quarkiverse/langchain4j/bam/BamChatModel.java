package io.quarkiverse.langchain4j.bam;

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
import dev.langchain4j.model.output.Response;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class BamChatModel implements ChatLanguageModel {

    private final String token;
    private final String modelId;
    private final String version;
    private final String decodingMethod;
    private final Integer minNewTokens;
    private final Integer maxNewTokens;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
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
        this.minNewTokens = config.minNewTokens;
        this.maxNewTokens = config.maxNewTokens;
        this.temperature = config.temperature;
        this.topP = config.topP;
        this.topK = config.topK;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        Parameters parameters = Parameters.builder().decodingMethod(decodingMethod).minNewTokens(minNewTokens)
                .maxNewTokens(maxNewTokens).temperature(temperature).build();

        TextGenerationRequest request = new TextGenerationRequest(modelId,
                messages.stream().map(cm -> new Message(getRole(cm), cm.text())).toList(), parameters);

        TextGenerationResponse textGenerationResponse = client.chat(request, token, version);

        return Response.from(AiMessage.from(textGenerationResponse.results().get(0).generatedText()));
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
        private Integer minNewTokens = 0;
        private Integer maxNewTokens = 200;
        private Double temperature;

        private URI url = URI.create("https://bam-api.res.ibm.com");
        private Integer topK;
        private Double topP;
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

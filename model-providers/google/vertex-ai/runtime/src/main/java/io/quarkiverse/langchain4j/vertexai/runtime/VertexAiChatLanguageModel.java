package io.quarkiverse.langchain4j.vertexai.runtime;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.runtime.LangChain4jUtil;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class VertexAiChatLanguageModel implements ChatModel {

    private final Parameters parameters;
    private final VertxAiRestApi.ApiMetadata apiMetadata;
    private final VertxAiRestApi restApi;

    private VertexAiChatLanguageModel(Builder builder) {
        this.parameters = Parameters.builder()
                .maxOutputTokens(builder.maxOutputTokens)
                .temperature(builder.temperature)
                .topK(builder.topK)
                .topP(builder.topP)
                .build();

        this.apiMetadata = VertxAiRestApi.ApiMetadata
                .builder()
                .modelId(builder.modelId)
                .location(builder.location)
                .projectId(builder.projectId)
                .publisher(builder.publisher)
                .build();

        try {
            String baseUrl = builder.baseUrl.orElse(String.format("https://%s-aiplatform.googleapis.com", builder.location));
            var restApiBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrl))
                    .connectTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS);


            if (builder.proxy != null) {
                if (builder.proxy.type() != Proxy.Type.HTTP) {
                    throw new IllegalArgumentException("Only HTTP type proxy is supported");
                }
                if (!(builder.proxy.address() instanceof InetSocketAddress)) {
                    throw new IllegalArgumentException("Unsupported proxy type");
                }
                InetSocketAddress socketAddress = (InetSocketAddress) builder.proxy.address();
                restApiBuilder.proxyAddress(socketAddress.getHostName(), socketAddress.getPort());
            }

            if (builder.logRequests || builder.logResponses) {
                restApiBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restApiBuilder.clientLogger(new VertxAiRestApi.VertxAiClientLogger(builder.logRequests,
                        builder.logResponses));
            }
            restApi = restApiBuilder.build(VertxAiRestApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        PredictRequest predictRequest = new PredictRequest(Collections.singletonList(
                new PredictRequest.ChatInstance(toContext(chatRequest.messages()), toVertexMessages(chatRequest.messages()))),
                parameters);

        PredictResponse predictResponse = restApi.predict(predictRequest, apiMetadata);

        return ChatResponse.builder()
                .aiMessage(AiMessage.from(predictResponse.predictions().get(0).candidates().get(0).content()))
                .tokenUsage(new TokenUsage(
                        predictResponse.metadata().tokenMetadata().inputTokenCount().totalTokens(),
                        predictResponse.metadata().tokenMetadata().outputTokenCount().totalTokens()))
                .build();
    }

    private static String toContext(List<ChatMessage> messages) {
        return messages.stream()
                .filter(chatMessage -> chatMessage.type() == SYSTEM)
                .map(LangChain4jUtil::chatMessageToText)
                .collect(joining("\n"));
    }

    private List<PredictRequest.Message> toVertexMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(chatMessage -> chatMessage.type() == USER || chatMessage.type() == AI)
                .map(chatMessage -> new PredictRequest.Message(chatMessage.type().name(),
                        LangChain4jUtil.chatMessageToText(chatMessage)))
                .collect(toList());
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static final class Builder {
        private Optional<String> baseUrl = Optional.empty();

        private String projectId;
        private String location;
        private String modelId;
        private String publisher;
        private Double temperature;
        private Integer maxOutputTokens;
        private Integer topK;
        private Double topP;
        private Duration timeout = Duration.ofSeconds(10);
        private Boolean logRequests = false;
        private Boolean logResponses = false;
        private Proxy proxy;

        public Builder baseUrl(Optional<String> baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder publisher(String publisher) {
            this.publisher = publisher;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
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

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public VertexAiChatLanguageModel build() {
            return new VertexAiChatLanguageModel(this);
        }
    }
}

package io.quarkiverse.langchain4j.openshiftai;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class OpenshiftAiChatModel implements ChatModel {
    public static final String TLS_TRUST_ALL = "quarkus.tls.trust-all";
    private final String modelId;
    private final OpenshiftAiRestApi client;

    public OpenshiftAiChatModel(Builder config) {
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(config.url)
                .connectTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS);

        if (config.logRequests || config.logResponses) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new OpenshiftAiRestApi.OpenshiftAiClientLogger(config.logRequests,
                    config.logResponses));
        }

        this.client = builder.build(OpenshiftAiRestApi.class);
        this.modelId = config.modelId;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {

        TextGenerationRequest request = new TextGenerationRequest(modelId, chatMessageToText(chatRequest.messages().get(0)));

        TextGenerationResponse textGenerationResponse = client.chat(request);

        return ChatResponse.builder().aiMessage(AiMessage.from(textGenerationResponse.generatedText())).build();
    }

    public static final class Builder {

        private String modelId;
        private Duration timeout = Duration.ofSeconds(15);

        private URI url;
        public boolean logResponses;
        public boolean logRequests;

        public Builder modelId(String modelId) {
            this.modelId = modelId;
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

        public OpenshiftAiChatModel build() {
            return new OpenshiftAiChatModel(this);
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

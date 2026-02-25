package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;
import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.chat.ChatClientContext;
import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatRestClient;
import com.ibm.watsonx.ai.chat.SseEventProcessor;
import com.ibm.watsonx.ai.chat.model.TextChatRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.QuarkusChatSubscriber;
import io.quarkiverse.langchain4j.watsonx.runtime.client.ChatRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusChatRestClient extends ChatRestClient {

    private final ChatRestApi client;

    QuarkusChatRestClient(Builder builder) {
        super(builder);
        try {

            var logCurl = QuarkusRestClientConfig.isLogCurl();
            var restClientBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(baseUrl).toURL())
                    .clientHeadersFactory(new BearerTokenHeaderFactory(authenticator))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            if (logRequests || logResponses || logCurl) {
                restClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restClientBuilder.clientLogger(new WatsonxClientLogger(logRequests, logResponses, logCurl));
            }

            client = restClientBuilder.build(ChatRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ChatResponse chat(String transactionId, TextChatRequest textChatRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<ChatResponse>() {
            @Override
            public ChatResponse call() throws Exception {
                return client.chat(UUID.randomUUID().toString(), transactionId, version, textChatRequest);
            }
        });
    }

    @Override
    public CompletableFuture<ChatResponse> chatStreaming(
            String transactionId,
            TextChatRequest textChatRequest,
            ChatClientContext context,
            ChatHandler handler) {

        var requestId = UUID.randomUUID().toString();
        var subscriber = new QuarkusChatSubscriber(
                new SseEventProcessor(textChatRequest.tools(), context.extractionTags()), handler);

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        client.chatStreaming(requestId, transactionId, version, textChatRequest)
                .onItem().invoke(new Consumer<String>() {
                    @Override
                    public void accept(String message) {
                        if (nonNull(message) && !message.isBlank()) {
                            subscriber.onNext("data: " + message);
                        }
                    }
                })
                .onFailure(WatsonxRestClientUtils::shouldRetry).retry().atMost(10)
                .onFailure().invoke(subscriber::onError)
                .onCompletion().invoke(() -> {
                    subscriber.onComplete().whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(response);
                        }
                    });
                })
                .collect().asList().replaceWithVoid()
                .subscribeAsCompletionStage();

        return future;
    }

    public static final class QuarkusChatRestClientBuilderFactory implements ChatRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusChatRestClient.Builder();
        }
    }

    static final class Builder extends ChatRestClient.Builder {
        @Override
        public ChatRestClient build() {
            return new QuarkusChatRestClient(this);
        }
    }
}

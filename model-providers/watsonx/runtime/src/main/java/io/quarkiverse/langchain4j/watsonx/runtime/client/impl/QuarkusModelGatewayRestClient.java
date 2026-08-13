package io.quarkiverse.langchain4j.watsonx.runtime.client.impl;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.retryOn;
import static java.util.Objects.nonNull;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import com.ibm.watsonx.ai.chat.ChatClientContext;
import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.SseEventProcessor;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatRequest;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatResponse;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayRestClient;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayTextChatRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.QuarkusChatSubscriber;
import io.quarkiverse.langchain4j.watsonx.runtime.client.GatewayRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusModelGatewayRestClient extends ModelGatewayRestClient {

    private final GatewayRestApi client;

    QuarkusModelGatewayRestClient(Builder builder) {
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

            client = restClientBuilder.build(GatewayRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ModelGatewayChatResponse chat(String transactionId, Duration timeout,
            ModelGatewayTextChatRequest gatewayRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<ModelGatewayChatResponse>() {
            @Override
            public ModelGatewayChatResponse call() throws Exception {
                return client.chat(requestId, transactionId, version, gatewayRequest);
            }
        });
    }

    @Override
    public CompletableFuture<ChatResponse> chatStreaming(
            String transactionId,
            Duration timeout,
            ModelGatewayTextChatRequest gatewayRequest,
            ChatClientContext<ModelGatewayChatRequest> context,
            ChatHandler handler) {

        var requestId = UUID.randomUUID().toString();
        var subscriber = new QuarkusChatSubscriber(
                new SseEventProcessor(gatewayRequest.tools(), context.extractionTags(), ModelGatewayChatResponse::builder),
                handler);

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        client.chatStreaming(requestId, transactionId, version, gatewayRequest)
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

    public static final class QuarkusModelGatewayRestClientBuilderFactory implements ModelGatewayRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusModelGatewayRestClient.Builder();
        }
    }

    static final class Builder extends ModelGatewayRestClient.Builder {
        @Override
        public ModelGatewayRestClient build() {
            return new QuarkusModelGatewayRestClient(this);
        }
    }
}

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

import com.ibm.watsonx.ai.textgeneration.TextGenerationHandler;
import com.ibm.watsonx.ai.textgeneration.TextGenerationResponse;
import com.ibm.watsonx.ai.textgeneration.TextGenerationRestClient;
import com.ibm.watsonx.ai.textgeneration.TextGenerationSubscriber;
import com.ibm.watsonx.ai.textgeneration.TextRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.TextGenerationRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusTextGenerationRestClient extends TextGenerationRestClient {

    private final TextGenerationRestApi client;

    QuarkusTextGenerationRestClient(Builder builder) {
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

            client = restClientBuilder.build(TextGenerationRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TextGenerationResponse generate(String transactionId, TextRequest textRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<TextGenerationResponse>() {
            @Override
            public TextGenerationResponse call() throws Exception {
                return client.generate(requestId, transactionId, version, textRequest);
            }
        });
    }

    @Override
    public CompletableFuture<Void> generateStreaming(String transactionId, TextRequest textRequest,
            TextGenerationHandler handler) {
        var requestId = UUID.randomUUID().toString();
        var subscriber = TextGenerationSubscriber.createSubscriber(handler);
        return client.generateStreaming(requestId, transactionId, version, textRequest)
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
                .onCompletion().invoke(subscriber::onComplete)
                .collect().asList().replaceWithVoid()
                .subscribeAsCompletionStage();
    }

    public static final class QuarkusTextGenerationRestClientBuilderFactory implements TextGenerationRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusTextGenerationRestClient.Builder();
        }
    }

    static final class Builder extends TextGenerationRestClient.Builder {
        @Override
        public TextGenerationRestClient build() {
            return new QuarkusTextGenerationRestClient(this);
        }
    }
}

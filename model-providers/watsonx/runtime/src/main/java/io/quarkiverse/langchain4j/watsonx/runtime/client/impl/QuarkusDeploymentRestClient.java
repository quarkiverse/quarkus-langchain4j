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
import com.ibm.watsonx.ai.chat.model.TextChatRequest;
import com.ibm.watsonx.ai.deployment.DeploymentResource;
import com.ibm.watsonx.ai.deployment.DeploymentRestClient;
import com.ibm.watsonx.ai.deployment.FindByIdRequest;
import com.ibm.watsonx.ai.textgeneration.TextGenerationHandler;
import com.ibm.watsonx.ai.textgeneration.TextGenerationResponse;
import com.ibm.watsonx.ai.textgeneration.TextGenerationSubscriber;
import com.ibm.watsonx.ai.textgeneration.TextRequest;
import com.ibm.watsonx.ai.timeseries.ForecastRequest;
import com.ibm.watsonx.ai.timeseries.ForecastResponse;

import io.quarkiverse.langchain4j.watsonx.runtime.QuarkusChatSubscriber;
import io.quarkiverse.langchain4j.watsonx.runtime.client.DeploymentRestApi;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils;
import io.quarkiverse.langchain4j.watsonx.runtime.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public final class QuarkusDeploymentRestClient extends DeploymentRestClient {

    private final DeploymentRestApi client;

    QuarkusDeploymentRestClient(Builder builder) {
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

            client = restClientBuilder.build(DeploymentRestApi.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DeploymentResource findById(FindByIdRequest parameters) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<DeploymentResource>() {
            @Override
            public DeploymentResource call() throws Exception {
                return client.findById(
                        parameters.deploymentId(),
                        requestId,
                        parameters.transactionId(),
                        parameters.projectId(),
                        parameters.spaceId(),
                        version);
            }
        });

    }

    @Override
    public TextGenerationResponse generate(String transactionId, String deploymentId, Duration timeout,
            TextRequest textRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<TextGenerationResponse>() {
            @Override
            public TextGenerationResponse call() throws Exception {
                return client.generate(deploymentId, requestId, transactionId, version, textRequest);
            }
        });
    }

    @Override
    public CompletableFuture<Void> generateStreaming(String transactionId, String deploymentId, Duration timeout,
            TextRequest textRequest, TextGenerationHandler handler) {
        var requestId = UUID.randomUUID().toString();
        var subscriber = TextGenerationSubscriber.createSubscriber(handler);

        return client.generateStreaming(deploymentId, requestId, transactionId, version, textRequest)
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
                .subscribe().asCompletionStage();
    }

    @Override
    public ChatResponse chat(String transactionId, String deploymentId, Duration timeout, TextChatRequest textChatRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<ChatResponse>() {
            @Override
            public ChatResponse call() throws Exception {
                return client.chat(deploymentId, requestId, transactionId, version, textChatRequest);
            }
        });
    }

    @Override
    public CompletableFuture<ChatResponse> chatStreaming(
            String transactionId,
            String deploymentId,
            TextChatRequest textChatRequest,
            ChatClientContext context,
            ChatHandler handler) {

        var requestId = UUID.randomUUID().toString();
        var subscriber = new QuarkusChatSubscriber(new SseEventProcessor(textChatRequest.tools(), context.extractionTags()),
                handler);

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        client.chatStreaming(deploymentId, requestId, transactionId, version, textChatRequest)
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

    @Override
    public ForecastResponse forecast(String transactionId, String deploymentId, Duration timeout,
            ForecastRequest forecastRequest) {
        var requestId = UUID.randomUUID().toString();
        return retryOn(requestId, new Callable<ForecastResponse>() {
            @Override
            public ForecastResponse call() throws Exception {
                return client.forecast(deploymentId, requestId, transactionId, version, forecastRequest);
            }
        });
    }

    public static final class QuarkusDeploymentRestClientBuilderFactory implements DeploymentRestClientBuilderFactory {
        @Override
        public Builder get() {
            return new QuarkusDeploymentRestClient.Builder();
        }
    }

    static final class Builder extends DeploymentRestClient.Builder {
        @Override
        public DeploymentRestClient build() {
            return new QuarkusDeploymentRestClient(this);
        }
    }
}

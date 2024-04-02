package io.quarkiverse.langchain4j.anthropic;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.model.anthropic.AnthropicMapper.toFinishReason;
import static java.util.Collections.synchronizedList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.AnthropicClient;
import dev.langchain4j.model.anthropic.AnthropicClientBuilderFactory;
import dev.langchain4j.model.anthropic.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.AnthropicHttpException;
import dev.langchain4j.model.anthropic.AnthropicStreamingData;
import dev.langchain4j.model.anthropic.AnthropicUsage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public class QuarkusAnthropicClient extends AnthropicClient {
    private final String apiKey;
    private final String anthropicVersion;
    private final AnthropicRestApi restApi;

    public QuarkusAnthropicClient(Builder builder) {
        this.apiKey = builder.apiKey;
        this.anthropicVersion = builder.version;

        try {
            var restApiBuilder = QuarkusRestClientBuilder.newBuilder().baseUri(new URI(builder.baseUrl))
                    .connectTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS);

            if (builder.logRequests || builder.logResponses) {
                restApiBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE).clientLogger(
                        new QuarkusAnthropicClient.AnthropicClientLogger(builder.logRequests, builder.logResponses));
            }

            this.restApi = restApiBuilder.build(AnthropicRestApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request) {
        return restApi.createMessage(request, createMetadata());
    }

    @Override
    public void createMessage(AnthropicCreateMessageRequest request, StreamingResponseHandler<AiMessage> handler) {
        restApi.streamMessage(request, createMetadata())
                .subscribe()
                .withSubscriber(new AnthropicStreamingSubscriber(handler));
    }

    private AnthropicRestApi.ApiMetadata createMetadata() {
        return AnthropicRestApi.ApiMetadata.builder()
                .apiKey(apiKey)
                .anthropicVersion(anthropicVersion)
                .build();
    }

    private static class AnthropicStreamingSubscriber implements MultiSubscriber<AnthropicStreamingData> {
        private final StreamingResponseHandler<AiMessage> handler;
        private Subscription subscription;
        private volatile AtomicReference<StringBuffer> contentBuilder = new AtomicReference<>(new StringBuffer());
        private volatile String stopReason;
        private final List<String> contents = synchronizedList(new ArrayList<>());
        private final AtomicInteger inputTokenCount = new AtomicInteger();
        private final AtomicInteger outputTokenCount = new AtomicInteger();

        private AnthropicStreamingSubscriber(StreamingResponseHandler<AiMessage> handler) {
            this.handler = handler;
        }

        @Override
        public void onItem(AnthropicStreamingData data) {
            switch (data.type) {
                case "message_start":
                    handleMessageStart(data);
                    break;

                case "content_block_start":
                    handleContentBlockStart(data);
                    break;

                case "content_block_delta":
                    handleContentBlockDelta(data);
                    break;

                case "content_block_stop":
                    handleContentBlockStop();
                    break;

                case "message_delta":
                    handleMessageDelta(data);
                    break;

                case "message_stop":
                    handleMessageStop();
                    break;

                case "error":
                    handleError(data);
                    break;
            }
        }

        private void handleMessageStart(AnthropicStreamingData data) {
            if ((data.message != null) && (data.message.usage != null)) {
                handleUsage(data.message.usage);
            }
        }

        private void handleUsage(AnthropicUsage usage) {
            if (usage.inputTokens != null) {
                inputTokenCount.addAndGet(usage.inputTokens);
            }

            if (usage.outputTokens != null) {
                outputTokenCount.addAndGet(usage.outputTokens);
            }
        }

        private void handleContentBlockStart(AnthropicStreamingData data) {
            if ((data.contentBlock != null) && "text".equals(data.contentBlock.type)) {
                var text = data.contentBlock.text;

                if (isNotNullOrEmpty(text)) {
                    contentBuilder.get().append(text);
                    handler.onNext(text);
                }
            }
        }

        private void handleContentBlockDelta(AnthropicStreamingData data) {
            if ((data.delta != null) && "text_delta".equals(data.delta.type)) {
                var text = data.delta.text;

                if (isNotNullOrEmpty(text)) {
                    contentBuilder.get().append(text);
                    handler.onNext(text);
                }
            }
        }

        private void handleContentBlockStop() {
            contents.add(contentBuilder.get().toString());
            contentBuilder.set(new StringBuffer());
        }

        private void handleMessageDelta(AnthropicStreamingData data) {
            if (data.delta != null) {
                var delta = data.delta;

                if (delta.stopReason != null) {
                    stopReason = delta.stopReason;
                }
            }

            if (data.usage != null) {
                handleUsage(data.usage);
            }
        }

        private void handleMessageStop() {
            var response = Response.from(
                    AiMessage.from(String.join("\n", contents)),
                    new TokenUsage(inputTokenCount.get(), outputTokenCount.get()),
                    toFinishReason(stopReason));

            handler.onComplete(response);
        }

        private void handleError(AnthropicStreamingData data) {
            onFailure(new AnthropicHttpException(null, "Got error processing data (%s)".formatted(data)));
        }

        @Override
        public void onFailure(Throwable failure) {
            handler.onError(failure);
        }

        @Override
        public void onCompletion() {
            handleMessageStop();
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            this.subscription.request(Long.MAX_VALUE);
        }
    }

    public static class QuarkusAnthropicClientBuilderFactory implements AnthropicClientBuilderFactory {
        @Override
        public AnthropicClient.Builder get() {
            return new Builder();
        }
    }

    public static class Builder extends AnthropicClient.Builder<QuarkusAnthropicClient, Builder> {
        @Override
        public QuarkusAnthropicClient build() {
            return new QuarkusAnthropicClient(this);
        }
    }

    /**
     * Introduce a custom logger as the stock one logs at the DEBUG level by default...
     */
    static class AnthropicClientLogger implements ClientLogger {
        private static final Logger log = Logger.getLogger(AnthropicClientLogger.class);

        private final boolean logRequests;
        private final boolean logResponses;

        public AnthropicClientLogger(boolean logRequests, boolean logResponses) {
            this.logRequests = logRequests;
            this.logResponses = logResponses;
        }

        @Override
        public void setBodySize(int bodySize) {
            // ignore
        }

        @Override
        public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
            if (logRequests && log.isInfoEnabled()) {
                try {
                    log.infof("Request:\n- method: %s\n- url: %s\n- headers: %s\n- body: %s", request.getMethod(),
                            request.absoluteURI(), inOneLine(request.headers()), bodyToString(body));
                } catch (Exception e) {
                    log.warn("Failed to log request", e);
                }
            }
        }

        @Override
        public void logResponse(HttpClientResponse response, boolean redirect) {
            if (logResponses && log.isInfoEnabled()) {
                response.bodyHandler(new Handler<>() {
                    @Override
                    public void handle(Buffer body) {
                        try {
                            log.infof("Response:\n- status code: %s\n- headers: %s\n- body: %s", response.statusCode(),
                                    inOneLine(response.headers()), bodyToString(body));
                        } catch (Exception e) {
                            log.warn("Failed to log response", e);
                        }
                    }
                });
            }
        }

        private String bodyToString(Buffer body) {
            return (body != null) ? body.toString() : "";
        }

        private String inOneLine(MultiMap headers) {
            return stream(headers.spliterator(), false)
                    .map(header -> {
                        var headerKey = header.getKey();
                        var headerValue = header.getValue();

                        if (headerKey.equals(AnthropicRestApi.API_KEY_HEADER)) {
                            headerValue = maskApiKeyHeaderValue(headerValue);
                        }

                        return "[%s: %s]".formatted(headerKey, headerValue);
                    })
                    .collect(joining(", "));
        }

        private static String maskApiKeyHeaderValue(String apiKeyHeaderValue) {
            try {
                if (apiKeyHeaderValue.length() <= 4) {
                    return apiKeyHeaderValue;
                }
                return apiKeyHeaderValue.substring(0, 2)
                        + "..."
                        + apiKeyHeaderValue.substring(apiKeyHeaderValue.length() - 2);
            } catch (Exception e) {
                return "Failed to mask the API key.";
            }
        }
    }
}

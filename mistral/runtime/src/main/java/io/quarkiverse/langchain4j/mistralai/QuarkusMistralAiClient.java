package io.quarkiverse.langchain4j.mistralai;

import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.finishReasonFrom;
import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.tokenUsageFrom;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.mistralai.MistralAiChatCompletionChoice;
import dev.langchain4j.model.mistralai.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.MistralAiClient;
import dev.langchain4j.model.mistralai.MistralAiClientBuilderFactory;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingRequest;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingResponse;
import dev.langchain4j.model.mistralai.MistralAiModelResponse;
import dev.langchain4j.model.mistralai.MistralAiUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public class QuarkusMistralAiClient extends MistralAiClient {

    private final String apiKey;
    private final MistralAiRestApi restApi;

    public QuarkusMistralAiClient(Builder builder) {
        this.apiKey = builder.apiKey;

        try {
            QuarkusRestClientBuilder restApiBuilder = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(builder.baseUrl))
                    .connectTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS);
            if (builder.logRequests || builder.logResponses) {
                restApiBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                restApiBuilder.clientLogger(new QuarkusMistralAiClient.MistralAiClientLogger(builder.logRequests,
                        builder.logResponses));
            }
            restApi = restApiBuilder.build(MistralAiRestApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MistralAiChatCompletionResponse chatCompletion(MistralAiChatCompletionRequest request) {
        return restApi.blockingChatCompletion(request, apiKey);
    }

    @Override
    public void streamingChatCompletion(MistralAiChatCompletionRequest request,
            StreamingResponseHandler<AiMessage> handler) {
        AtomicReference<StringBuffer> contentBuilder = new AtomicReference<>(new StringBuffer());
        AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>();
        AtomicReference<FinishReason> finishReason = new AtomicReference<>();
        restApi.streamingChatCompletion(request, apiKey).subscribe().with(new Consumer<>() {
            @Override
            public void accept(MistralAiChatCompletionResponse response) {
                MistralAiChatCompletionChoice choice = response.getChoices().get(0);
                String chunk = choice.getDelta().getContent();
                contentBuilder.get().append(chunk);
                handler.onNext(chunk);

                MistralAiUsage usageInfo = response.getUsage();
                if (usageInfo != null) {
                    tokenUsage.set(tokenUsageFrom(usageInfo));
                }

                String finishReasonString = choice.getFinishReason();
                if (finishReasonString != null) {
                    finishReason.set(finishReasonFrom(finishReasonString));
                }
            }
        }, new Consumer<>() {
            @Override
            public void accept(Throwable t) {
                handler.onError(t);
            }
        }, new Runnable() {
            @Override
            public void run() {
                Response<AiMessage> response = Response.from(
                        AiMessage.from(contentBuilder.get().toString()),
                        tokenUsage.get(),
                        finishReason.get());
                handler.onComplete(response);
            }
        });
    }

    @Override
    public MistralAiEmbeddingResponse embedding(MistralAiEmbeddingRequest request) {
        return restApi.embedding(request, apiKey);
    }

    @Override
    public MistralAiModelResponse listModels() {
        return restApi.models(apiKey);
    }

    public static class QuarkusMistralAiClientBuilderFactory implements MistralAiClientBuilderFactory {

        @Override
        public Builder get() {
            return new Builder();
        }
    }

    public static class Builder extends MistralAiClient.Builder<QuarkusMistralAiClient, Builder> {

        @Override
        public QuarkusMistralAiClient build() {
            return new QuarkusMistralAiClient(this);
        }
    }

    /**
     * Introduce a custom logger as the stock one logs at the DEBUG level by default...
     */
    static class MistralAiClientLogger implements ClientLogger {
        private static final Logger log = Logger.getLogger(MistralAiClientLogger.class);

        private static final Pattern BEARER_PATTERN = Pattern.compile("(Bearer\\s*)(\\w{2})(\\w+)(\\w{2})");

        private final boolean logRequests;
        private final boolean logResponses;

        public MistralAiClientLogger(boolean logRequests, boolean logResponses) {
            this.logRequests = logRequests;
            this.logResponses = logResponses;
        }

        @Override
        public void setBodySize(int bodySize) {
            // ignore
        }

        @Override
        public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
            if (!logRequests || !log.isInfoEnabled()) {
                return;
            }
            try {
                log.infof("Request:\n- method: %s\n- url: %s\n- headers: %s\n- body: %s",
                        request.getMethod(),
                        request.absoluteURI(),
                        inOneLine(request.headers()),
                        bodyToString(body));
            } catch (Exception e) {
                log.warn("Failed to log request", e);
            }
        }

        @Override
        public void logResponse(HttpClientResponse response, boolean redirect) {
            if (!logResponses || !log.isInfoEnabled()) {
                return;
            }
            response.bodyHandler(new Handler<>() {
                @Override
                public void handle(Buffer body) {
                    try {
                        log.infof(
                                "Response:\n- status code: %s\n- headers: %s\n- body: %s",
                                response.statusCode(),
                                inOneLine(response.headers()),
                                bodyToString(body));
                    } catch (Exception e) {
                        log.warn("Failed to log response", e);
                    }
                }
            });
        }

        private String bodyToString(Buffer body) {
            if (body == null) {
                return "";
            }
            return body.toString();
        }

        private String inOneLine(MultiMap headers) {

            return stream(headers.spliterator(), false)
                    .map(header -> {
                        String headerKey = header.getKey();
                        String headerValue = header.getValue();
                        if (headerKey.equals("Authorization")) {
                            headerValue = maskAuthorizationHeaderValue(headerValue);
                        }
                        return String.format("[%s: %s]", headerKey, headerValue);
                    })
                    .collect(joining(", "));
        }

        private static String maskAuthorizationHeaderValue(String authorizationHeaderValue) {
            try {

                Matcher matcher = BEARER_PATTERN.matcher(authorizationHeaderValue);

                StringBuilder sb = new StringBuilder();
                while (matcher.find()) {
                    matcher.appendReplacement(sb, matcher.group(1) + matcher.group(2) + "..." + matcher.group(4));
                }
                matcher.appendTail(sb);

                return sb.toString();
            } catch (Exception e) {
                return "Failed to mask the API key.";
            }
        }
    }
}

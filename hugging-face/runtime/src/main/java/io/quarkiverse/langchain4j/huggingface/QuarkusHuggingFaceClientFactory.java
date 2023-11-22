package io.quarkiverse.langchain4j.huggingface;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public class QuarkusHuggingFaceClientFactory implements HuggingFaceClientFactory {

    @Override
    public HuggingFaceClient create(Input input) {
        throw new UnsupportedOperationException("Should not be called");
    }

    public HuggingFaceClient create(QuarkusHuggingFaceChatModel.Builder config, Input input, URI url) {
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(url)
                .connectTimeout(input.timeout().toSeconds(), TimeUnit.SECONDS)
                .readTimeout(input.timeout().toSeconds(), TimeUnit.SECONDS);

        if (config != null && (config.logRequests || config.logResponses)) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new HuggingFaceClientLogger(config.logRequests,
                    config.logResponses));
        }

        HuggingFaceRestApi restApi = builder
                .build(HuggingFaceRestApi.class);

        return new QuarkusHuggingFaceClient(restApi, input.apiKey());
    }

    public static class QuarkusHuggingFaceClient implements HuggingFaceClient {

        private final HuggingFaceRestApi restApi;
        private final String token;

        public QuarkusHuggingFaceClient(HuggingFaceRestApi restApi, String token) {
            this.restApi = restApi;
            this.token = token;
        }

        @Override
        public TextGenerationResponse chat(TextGenerationRequest request) {
            return generate(request);
        }

        @Override
        public TextGenerationResponse generate(TextGenerationRequest request) {
            return toOneResponse(restApi.generate(request, token));
        }

        private static TextGenerationResponse toOneResponse(List<TextGenerationResponse> responses) {
            if (responses != null && responses.size() == 1) {
                return responses.get(0);
            } else {
                throw new RuntimeException(
                        "Expected only one generated_text, but was: " + (responses == null ? 0 : responses.size()));
            }
        }

        @Override
        public List<float[]> embed(EmbeddingRequest request) {
            return restApi.embed(request, token);
        }
    }

    /**
     * Introduce a custom logger as the stock one logs at the DEBUG level by default...
     */
    class HuggingFaceClientLogger implements ClientLogger {
        private static final Logger log = Logger.getLogger(HuggingFaceClientLogger.class);

        private static final Pattern BEARER_PATTERN = Pattern.compile("(Bearer\\s*sk-)(\\w{2})(\\w+)(\\w{2})");

        private final boolean logRequests;
        private final boolean logResponses;

        public HuggingFaceClientLogger(boolean logRequests, boolean logResponses) {
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
                        } else if (headerKey.equals("api-key")) {
                            headerValue = maskApiKeyHeaderValue(headerValue);
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

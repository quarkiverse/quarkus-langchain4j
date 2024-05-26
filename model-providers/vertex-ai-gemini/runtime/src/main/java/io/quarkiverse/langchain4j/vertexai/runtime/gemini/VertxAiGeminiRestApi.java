package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestFilter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;

import io.quarkus.arc.DefaultBean;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.quarkus.security.credential.TokenCredential;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

@Path("v1/projects/{projectId}/locations/{location}/publishers/{publisher}/models")
@RegisterProvider(VertxAiGeminiRestApi.TokenFilter.class)
public interface VertxAiGeminiRestApi {

    @Path("{modelId}:generateContent")
    @POST
    GenerateContentResponse generateContent(GenerateContentRequest request, @BeanParam ApiMetadata apiMetadata);

    @ClientObjectMapper
    static ObjectMapper mapper(ObjectMapper defaultObjectMapper) {
        return defaultObjectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    class ApiMetadata {
        @RestPath
        public final String projectId;

        @RestPath
        public final String location;

        @RestPath
        public final String modelId;

        @RestPath
        public final String publisher;

        private ApiMetadata(Builder builder) {
            this.projectId = builder.projectId;
            this.location = builder.location;
            this.modelId = builder.modelId;
            this.publisher = builder.publisher;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String projectId;
            private String location;
            private String modelId;
            private String publisher;

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

            public Builder publisher(String publisherId) {
                this.publisher = publisherId;
                return this;
            }

            public ApiMetadata build() {
                return new ApiMetadata(this);
            }
        }
    }

    interface AuthProvider {

        String getBearerToken();
    }

    @ApplicationScoped
    @DefaultBean
    class ApplicationDefaultAuthProvider implements AuthProvider {

        @Override
        public String getBearerToken() {
            try {
                var credentials = GoogleCredentials.getApplicationDefault();
                credentials.refreshIfExpired();
                return credentials.getAccessToken().getTokenValue();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    class TokenFilter implements ResteasyReactiveClientRequestFilter {

        private final ExecutorService executorService;
        private final AuthProvider authProvider;

        @Inject
        Instance<TokenCredential> tokenCredential;

        public TokenFilter(ExecutorService executorService, AuthProvider authProvider) {
            this.executorService = executorService;
            this.authProvider = authProvider;
        }

        @Override
        public void filter(ResteasyReactiveClientRequestContext context) {
            context.suspend();
            final String currentToken = tokenCredential.isResolvable() ? tokenCredential.get().getToken() : null;
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        context.getHeaders().add("Authorization", "Bearer " +
                                (currentToken != null ? currentToken : authProvider.getBearerToken()));
                        context.resume();
                    } catch (Exception e) {
                        context.resume(e);
                    }
                }
            });
        }
    }

    class VertxAiClientLogger implements ClientLogger {
        private static final Logger log = Logger.getLogger(VertxAiClientLogger.class);

        private static final Pattern BEARER_PATTERN = Pattern.compile("(Bearer\\s*)(\\w{2})(\\w|\\.|-|_)+(\\w{2})");

        private final boolean logRequests;
        private final boolean logResponses;

        public VertxAiClientLogger(boolean logRequests, boolean logResponses) {
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

package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.client.SseEvent;
import org.jboss.resteasy.reactive.client.api.ClientLogger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.gemini.common.EmbedContentRequest;
import io.quarkiverse.langchain4j.gemini.common.EmbedContentRequests;
import io.quarkiverse.langchain4j.gemini.common.EmbedContentResponse;
import io.quarkiverse.langchain4j.gemini.common.EmbedContentResponses;
import io.quarkiverse.langchain4j.gemini.common.GenerateContentRequest;
import io.quarkiverse.langchain4j.gemini.common.GenerateContentResponse;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

@Path("v1/projects/{projectId}/locations/{location}/publishers/{publisher}/models")
public interface VertxAiGeminiRestApi {

    @Path("{modelId}:generateContent")
    @POST
    GenerateContentResponse generateContent(GenerateContentRequest request, @BeanParam ApiMetadata apiMetadata);

    @Path("{modelId}:batchEmbedContents")
    @POST
    EmbedContentResponses batchEmbedContents(EmbedContentRequests embedContentRequest, @BeanParam ApiMetadata apiMetadata);

    @Path("{modelId}:embedContent")
    @POST
    EmbedContentResponse embedContent(EmbedContentRequest embedContentRequest, @BeanParam ApiMetadata apiMetadata);

    @Path("{modelId}:streamGenerateContent")
    @POST
    @Produces(MediaType.SERVER_SENT_EVENTS)
    Multi<SseEvent<GenerateContentResponse>> generateContentStream(GenerateContentRequest request,
            @BeanParam ApiMetadata apiMetadata, @QueryParam("alt") String sse);

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

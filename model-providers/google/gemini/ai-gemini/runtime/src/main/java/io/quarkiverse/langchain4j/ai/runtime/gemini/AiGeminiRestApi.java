package io.quarkiverse.langchain4j.ai.runtime.gemini;

import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
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
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

@Path("models/")
public interface AiGeminiRestApi {

    @Path("{modelId}:batchEmbedContents")
    @POST
    EmbedContentResponses batchEmbedContents(EmbedContentRequests embedContentRequest, @BeanParam ApiMetadata apiMetadata);

    @Path("{modelId}:embedContent")
    @POST
    EmbedContentResponse embedContent(EmbedContentRequest embedContentRequest, @BeanParam ApiMetadata apiMetadata);

    @Path("{modelId}:generateContent")
    @POST
    GenerateContentResponse generateContent(GenerateContentRequest request, @BeanParam ApiMetadata apiMetadata);

    @Path("{modelId}:streamGenerateContent")
    @POST
    @Produces(MediaType.SERVER_SENT_EVENTS)
    Multi<SseEvent<GenerateContentResponse>> generateContentStream(GenerateContentRequest request,
            @BeanParam ApiMetadata apiMetadata, @QueryParam("alt") String sse);

    @ClientExceptionMapper
    static ClientWebApplicationException toException(Response response) {
        if (response.getStatus() == 400) {
            final ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            ErrorResponse.ErrorInfo errorInfo = errorResponse.error();
            return new ClientWebApplicationException(errorInfo.status() + ": " + errorInfo.message());

        }

        return null;
    }

    @ClientObjectMapper
    static ObjectMapper mapper(ObjectMapper defaultObjectMapper) {
        return defaultObjectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    class ApiMetadata {

        @RestQuery
        public final String key;

        @RestPath
        public final String modelId;

        private ApiMetadata(Builder builder) {
            this.key = builder.key;
            this.modelId = builder.modelId;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private String key;
            private String modelId;

            public Builder key(String key) {
                this.key = key;
                return this;
            }

            public Builder modelId(String modelId) {
                this.modelId = modelId;
                return this;
            }

            public ApiMetadata build() {
                return new ApiMetadata(this);
            }
        }
    }

    class AiClientLogger implements ClientLogger {

        private static final Logger log = Logger.getLogger(AiClientLogger.class);

        private final boolean logRequests;
        private final boolean logResponses;

        public AiClientLogger(boolean logRequests, boolean logResponses) {
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
                        (request.headers()),
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
                                (response.headers()),
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

    }
}

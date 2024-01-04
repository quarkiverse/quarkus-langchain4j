package io.quarkiverse.langchain4j.openai;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Priority;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.client.SseEvent;
import org.jboss.resteasy.reactive.client.SseEventFilter;
import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.common.providers.serialisers.AbstractJsonMessageBodyReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import dev.ai4j.openai4j.OpenAiHttpException;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.ai4j.openai4j.embedding.EmbeddingRequest;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import dev.ai4j.openai4j.image.GenerateImagesRequest;
import dev.ai4j.openai4j.image.GenerateImagesResponse;
import dev.ai4j.openai4j.moderation.ModerationRequest;
import dev.ai4j.openai4j.moderation.ModerationResponse;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

/**
 * This Microprofile REST client is used as the building block of all the API calls to OpenAI.
 * The implementation is provided by the Reactive REST Client in Quarkus.
 */

@Path("")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RegisterProvider(OpenAiRestApi.OpenAiRestApiJacksonReader.class)
@RegisterProvider(OpenAiRestApi.OpenAiRestApiJacksonWriter.class)
@RegisterProvider(OpenAiRestApi.OpenAiRestApiReaderInterceptor.class)
@RegisterProvider(OpenAiRestApi.OpenAiRestApiWriterInterceptor.class)
public interface OpenAiRestApi {

    /**
     * Perform a non-blocking request for a completion response
     */
    @Path("completions")
    @POST
    Uni<CompletionResponse> completion(CompletionRequest request, @BeanParam ApiMetadata input);

    /**
     * Perform a blocking request for a completion response
     */
    @Path("completions")
    @POST
    CompletionResponse blockingCompletion(CompletionRequest request, @BeanParam ApiMetadata input);

    /**
     * Performs a non-blocking request for a streaming completion request
     */
    @Path("chat/completions")
    @POST
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @SseEventFilter(DoneFilter.class)
    Multi<CompletionResponse> streamingCompletion(CompletionRequest request, @BeanParam ApiMetadata input);

    /**
     * Perform a non-blocking request for a chat completion response
     */
    @Path("chat/completions")
    @POST
    Uni<ChatCompletionResponse> createChatCompletion(ChatCompletionRequest request, @BeanParam ApiMetadata input);

    /**
     * Perform a blocking request for a chat completion response
     */
    @Path("chat/completions")
    @POST
    ChatCompletionResponse blockingChatCompletion(ChatCompletionRequest request, @BeanParam ApiMetadata input);

    /**
     * Performs a non-blocking request for a streaming chat completion request
     */
    @Path("chat/completions")
    @POST
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @SseEventFilter(DoneFilter.class)
    Multi<ChatCompletionResponse> streamingChatCompletion(ChatCompletionRequest request, @BeanParam ApiMetadata input);

    /**
     * Perform a non-blocking request to get the embeddings of an input text
     */
    @Path("embeddings")
    @POST
    Uni<EmbeddingResponse> embedding(EmbeddingRequest request, @BeanParam ApiMetadata input);

    /**
     * Perform a blocking request to get the embeddings of an input text
     */
    @Path("embeddings")
    @POST
    EmbeddingResponse blockingEmbedding(EmbeddingRequest request, @BeanParam ApiMetadata input);

    /**
     * Perform a non-blocking request to get a moderated version of an input text
     */
    @Path("moderations")
    @POST
    Uni<ModerationResponse> moderation(ModerationRequest request, @BeanParam ApiMetadata input);

    /**
     * Perform a blocking request to get a moderated version of an input text
     */
    @Path("moderations")
    @POST
    ModerationResponse blockingModeration(ModerationRequest request, @BeanParam ApiMetadata input);

    @Path("images/generations")
    @POST
    GenerateImagesResponse blockingImagesGenerations(GenerateImagesRequest request, @BeanParam ApiMetadata input);

    @Path("images/generations")
    @POST
    Uni<GenerateImagesResponse> imagesGenerations(GenerateImagesRequest request, @BeanParam ApiMetadata input);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            return new OpenAiHttpException(response.getStatus(), response.readEntity(String.class));
        }
        return null;
    }

    /**
     * Ensures that the terminal event sent by OpenAI is not processed (as it is not a valid json event)
     */
    class DoneFilter implements Predicate<SseEvent<String>> {

        @Override
        public boolean test(SseEvent<String> event) {
            return !"[DONE]".equals(event.data());
        }
    }

    @Priority(Priorities.USER + 100) // this priority ensures that our Writer has priority over the standard Jackson one
    class OpenAiRestApiJacksonWriter implements MessageBodyWriter<Object> {

        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return true;
        }

        @Override
        public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
                throws IOException, WebApplicationException {
            entityStream.write(ObjectMapperHolder.MAPPER.writeValueAsString(o).getBytes(StandardCharsets.UTF_8));
        }
    }

    @Priority(Priorities.USER - 100) // this priority ensures that our Reader has priority over the standard Jackson one
    class OpenAiRestApiJacksonReader extends AbstractJsonMessageBodyReader {

        /**
         * Normally this is not necessary, but if one uses the 'demo' Langchain4j key, then the response comes back as type
         * text/html
         * but the content is still JSON.
         */
        @Override
        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return true;
        }

        /**
         * We need a custom version of the Jackson provider because reading SSE values does not work properly with
         * {@code @ClientObjectMapper} due to the lack of a complete context in those requests
         */
        @Override
        public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
                throws IOException, WebApplicationException {
            return ObjectMapperHolder.READER
                    .forType(ObjectMapperHolder.READER.getTypeFactory().constructType(genericType != null ? genericType : type))
                    .readValue(entityStream);
        }
    }

    public class ObjectMapperHolder {
        public static final ObjectMapper MAPPER = QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;

        private static final ObjectReader READER = MAPPER.reader();
    }

    /**
     * This method validates that the response is not empty, which happens when the API returns an error object
     */
    class OpenAiRestApiReaderInterceptor implements ReaderInterceptor {

        @Override
        public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
            return validateResponse(context.proceed());
        }

        /**
         * The purpose of this method is to ensure that the API response contains a valid object.
         * This is needed because OpenAI sometime returns HTTP 200 but with a json response
         * that contains an error object and therefore Jackson does not set any properties.
         *
         * @return result if it is valid
         */
        private Object validateResponse(Object result) {
            if (result instanceof ChatCompletionResponse) {
                ChatCompletionResponse r = (ChatCompletionResponse) result;
                if (r.id() == null) {
                    throw new OpenAiApiException(ChatCompletionResponse.class);
                }
            } else if (result instanceof CompletionResponse) {
                CompletionResponse r = (CompletionResponse) result;
                if (r.id() == null) {
                    throw new OpenAiApiException(CompletionResponse.class);
                }
            } else if (result instanceof ModerationResponse) {
                ModerationResponse r = (ModerationResponse) result;
                if (r.id() == null) {
                    throw new OpenAiApiException(ModerationResponse.class);
                }
            }
            return result;
        }

    }

    /**
     * The point of this is to properly set the {@code stream} value of the request
     * so users don't have to remember to set it manually
     */
    class OpenAiRestApiWriterInterceptor implements WriterInterceptor {
        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
            Object entity = context.getEntity();
            if (entity instanceof ChatCompletionRequest) {
                ChatCompletionRequest request = (ChatCompletionRequest) entity;
                MultivaluedMap<String, Object> headers = context.getHeaders();
                List<Object> acceptList = headers.get(HttpHeaders.ACCEPT);
                if ((acceptList != null) && (acceptList.size() == 1)) {
                    String accept = (String) acceptList.get(0);
                    if (MediaType.APPLICATION_JSON.equals(accept)) {
                        if (Boolean.TRUE.equals(request.stream())) {
                            context.setEntity(ChatCompletionRequest.builder().from(request).stream(null).build());
                        }
                    } else if (MediaType.SERVER_SENT_EVENTS.equals(accept)) {
                        if (!Boolean.TRUE.equals(request.stream())) {
                            context.setEntity(ChatCompletionRequest.builder().from(request).stream(true).build());
                        }
                    }
                }
            }
            context.proceed();
        }
    }

    /**
     * Introduce a custom logger as the stock one logs at the DEBUG level by default...
     */
    class OpenAiClientLogger implements ClientLogger {
        private static final Logger log = Logger.getLogger(OpenAiClientLogger.class);

        private static final Pattern BEARER_PATTERN = Pattern.compile("(Bearer\\s*sk-)(\\w{2})(\\w+)(\\w{2})");

        private final boolean logRequests;
        private final boolean logResponses;

        public OpenAiClientLogger(boolean logRequests, boolean logResponses) {
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

    class ApiMetadata {

        @HeaderParam("Authorization")
        public final String authorization;

        @HeaderParam("api-key")
        public final String apiKey;
        @QueryParam("api-version")
        public final String apiVersion;

        @HeaderParam("OpenAI-Organization")
        public final String organizationId;

        private ApiMetadata(String authorization, String apiKey,
                String apiVersion, String organizationId) {
            this.authorization = authorization;
            this.apiKey = apiKey;
            this.apiVersion = apiVersion;
            this.organizationId = organizationId;
        }

        public static ApiMetadata.Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String apiKey;
            private String apiVersion;
            private String organizationId;

            public ApiMetadata build() {
                return (apiKey == null) ? new ApiMetadata(null, null, apiVersion, organizationId)
                        : new ApiMetadata(
                                "Bearer " + apiKey, // typical OpenAI authentication
                                apiKey, // used by AzureAI
                                apiVersion, organizationId);
            }

            public ApiMetadata.Builder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public ApiMetadata.Builder apiVersion(String apiVersion) {
                this.apiVersion = apiVersion;
                return this;
            }

            public ApiMetadata.Builder organizationId(String organizationId) {
                this.organizationId = organizationId;
                return this;
            }
        }
    }
}

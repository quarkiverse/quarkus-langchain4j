package io.quarkiverse.langchain4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.common.providers.serialisers.AbstractJsonMessageBodyReader;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import dev.ai4j.openai4j.OpenAiHttpException;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.ai4j.openai4j.embedding.EmbeddingRequest;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import dev.ai4j.openai4j.moderation.ModerationRequest;
import dev.ai4j.openai4j.moderation.ModerationResponse;
import io.quarkus.arc.Arc;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.NotBody;
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
@ClientHeaderParam(name = "Authorization", value = "Bearer {token}")
@ClientHeaderParam(name = "api-key", value = "{token}") // used by AzureAI
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface QuarkusRestApi {

    /**
     * Perform a non-blocking request for a completion response
     */
    @Path("completions")
    @POST
    Uni<CompletionResponse> completion(CompletionRequest request, @NotBody String token);

    /**
     * Perform a blocking request for a completion response
     */
    @Path("completions")
    @POST
    CompletionResponse blockingCompletion(CompletionRequest request, @NotBody String token);

    /**
     * Performs a non-blocking request for a streaming completion request
     */
    @Path("chat/completions")
    @POST
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<CompletionResponse> streamingCompletion(CompletionRequest request, @NotBody String token);

    /**
     * Perform a non-blocking request for a chat completion response
     */
    @Path("chat/completions")
    @POST
    Uni<ChatCompletionResponse> createChatCompletion(ChatCompletionRequest request, @NotBody String token);

    /**
     * Perform a blocking request for a chat completion response
     */
    @Path("chat/completions")
    @POST
    ChatCompletionResponse blockingChatCompletion(ChatCompletionRequest request, @NotBody String token);

    /**
     * Performs a non-blocking request for a streaming chat completion request
     */
    @Path("chat/completions")
    @POST
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<ChatCompletionResponse> streamingChatCompletion(ChatCompletionRequest request, @NotBody String token);

    /**
     * Perform a non-blocking request to get the embeddings of an input text
     */
    @Path("embeddings")
    @POST
    Uni<EmbeddingResponse> embedding(EmbeddingRequest request, @NotBody String token);

    /**
     * Perform a blocking request to get the embeddings of an input text
     */
    @Path("embeddings")
    @POST
    EmbeddingResponse blockingEmbedding(EmbeddingRequest request, @NotBody String token);

    /**
     * Perform a non-blocking request to get a moderated version of an input text
     */
    @Path("moderations")
    @POST
    Uni<ModerationResponse> moderation(ModerationRequest request, @NotBody String token);

    /**
     * Perform a blocking request to get a moderated version of an input text
     */
    @Path("moderations")
    @POST
    ModerationResponse blockingModeration(ModerationRequest request, @NotBody String token);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            return new OpenAiHttpException(response.getStatus(), response.readEntity(String.class));
        }
        return null;
    }

    /**
     * We need a custom version of the Jackson provider because reading SSE values does not work properly with
     * {@code @ClientObjectMapper} due to the lack of a complete context in those requests
     */
    @Provider
    @ConstrainedTo(RuntimeType.CLIENT)
    @Priority(Priorities.USER + 100)
    class OpenAiRestApiJacksonProvider extends AbstractJsonMessageBodyReader implements MessageBodyWriter<Object> {

        @Override
        public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
                throws IOException, WebApplicationException {
            return ObjectMapperHolder.READER
                    .forType(ObjectMapperHolder.READER.getTypeFactory().constructType(genericType != null ? genericType : type))
                    .readValue(entityStream);
        }

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

        public static class ObjectMapperHolder {
            public static final ObjectMapper MAPPER = Arc.container().instance(ObjectMapper.class).get()
                    .copy()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

            private static final ObjectReader READER = MAPPER.reader();
        }
    }

    /**
     * This method does two things:
     * <p>
     * First, it returns {@code null} instead of throwing an exception when last streaming API result comes back and.
     * This result is a "[DONE]" message, so it cannot map onto the domain.
     * Second, it validates that the response is not empty, which happens when the API returns an error object
     */
    @Provider
    @ConstrainedTo(RuntimeType.CLIENT)
    class OpenAiRestApiReaderInterceptor implements ReaderInterceptor {

        @Override
        public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
            try {
                return validateResponse(context.proceed());
            } catch (ProcessingException e) {
                Throwable cause = e.getCause();
                if (cause instanceof MismatchedInputException) {
                    Class<?> targetType = ((MismatchedInputException) cause).getTargetType();
                    if (ChatCompletionResponse.Builder.class.equals(targetType)
                            || CompletionResponse.Builder.class.equals(targetType)) {
                        if (cause.getMessage().contains("DONE") || cause.getMessage()
                                .contains("JsonToken.START_ARRAY")) {
                            return null;
                        }
                    }
                }

                throw e;
            }
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
    @Provider
    @ConstrainedTo(RuntimeType.CLIENT)
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

        private int bodySize;

        private final boolean logRequests;
        private final boolean logResponses;

        public OpenAiClientLogger(boolean logRequests, boolean logResponses) {
            this.logRequests = logRequests;
            this.logResponses = logResponses;
        }

        @Override
        public void setBodySize(int bodySize) {
            this.bodySize = bodySize;
        }

        @Override
        public void logResponse(HttpClientResponse response, boolean redirect) {
            if (!logResponses || !log.isInfoEnabled()) {
                return;
            }
            response.bodyHandler(new Handler<>() {
                @Override
                public void handle(Buffer body) {
                    log.infof("%s: %s %s, Status[%d %s], Headers[%s], Body:\n%s",
                            redirect ? "Redirect" : "Response",
                            response.request().getMethod(), response.request().absoluteURI(), response.statusCode(),
                            response.statusMessage(), asString(response.headers()), bodyToString(body));
                }
            });
        }

        @Override
        public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
            if (!logRequests || !log.isInfoEnabled()) {
                return;
            }
            if (omitBody) {
                log.infof("Request: %s %s Headers[%s], Body omitted",
                        request.getMethod(), request.absoluteURI(), asString(request.headers()));
            } else if (body == null || body.length() == 0) {
                log.infof("Request: %s %s Headers[%s], Empty body",
                        request.getMethod(), request.absoluteURI(), asString(request.headers()));
            } else {
                log.infof("Request: %s %s Headers[%s], Body:\n%s",
                        request.getMethod(), request.absoluteURI(), asString(request.headers()), bodyToString(body));
            }
        }

        private String bodyToString(Buffer body) {
            if (body == null) {
                return "";
            } else if (bodySize <= 0) {
                return body.toString();
            } else {
                String bodyAsString = body.toString();
                return bodyAsString.substring(0, Math.min(bodySize, bodyAsString.length()));
            }
        }

        private String asString(MultiMap headers) {
            if (headers.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder((headers.size() * (6 + 1 + 6)) + (headers.size() - 1)); // this is a very rough estimate of a result like 'key1=value1 key2=value2'
            boolean isFirst = true;
            for (Map.Entry<String, String> entry : headers) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    sb.append(' ');
                }
                sb.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return sb.toString();
        }
    }
}

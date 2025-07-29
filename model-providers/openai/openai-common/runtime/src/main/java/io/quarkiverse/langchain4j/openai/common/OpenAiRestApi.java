package io.quarkiverse.langchain4j.openai.common;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

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

import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.client.SseEvent;
import org.jboss.resteasy.reactive.client.SseEventFilter;
import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestFilter;
import org.jboss.resteasy.reactive.common.providers.serialisers.AbstractJsonMessageBodyReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.completion.CompletionResponse;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.moderation.ModerationRequest;
import dev.langchain4j.model.openai.internal.moderation.ModerationResponse;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
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
            return new HttpException(response.getStatus(), response.readEntity(String.class));
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

    class OpenAIRestAPIFilter implements ResteasyReactiveClientRequestFilter {
        ModelAuthProvider authorizer;

        public OpenAIRestAPIFilter(ModelAuthProvider authorizer) {
            this.authorizer = authorizer;
        }

        @Override
        public void filter(ResteasyReactiveClientRequestContext requestContext) {
            Executor executorService = createExecutor();
            requestContext.suspend();
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        setAuthorization(requestContext);
                        requestContext.resume();
                    } catch (Exception e) {
                        requestContext.resume(e);
                    }
                }
            });
        }

        private Executor createExecutor() {
            InstanceHandle<ManagedExecutor> executor = Arc.container().instance(ManagedExecutor.class);
            return executor.isAvailable() ? executor.get() : Infrastructure.getDefaultExecutor();
        }

        private void setAuthorization(ResteasyReactiveClientRequestContext requestContext) {
            String authValue = authorizer.getAuthorization(new AuthInputImpl(requestContext.getMethod(),
                    requestContext.getUri(), requestContext.getHeaders()));
            if (authValue != null) {
                requestContext.getHeaders().putSingle("Authorization", authValue);
            }
        }

        private record AuthInputImpl(
                String method,
                URI uri,
                MultivaluedMap<String, Object> headers) implements ModelAuthProvider.Input {
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
         * Normally this is not necessary, but if one uses the 'demo' LangChain4j key, then the response comes back as type
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
            if (result instanceof ChatCompletionResponse r) {
                if (r.id() == null) {
                    throw new OpenAiApiException(ChatCompletionResponse.class);
                }
            } else if (result instanceof CompletionResponse r) {
                if (r.id() == null) {
                    throw new OpenAiApiException(CompletionResponse.class);
                }
            } else if (result instanceof ModerationResponse r) {
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
            if (entity instanceof ChatCompletionRequest request) {
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
                        headerValue = switch (headerKey) {
                            case "Authorization", "api-key" -> maskHeaderValue(headerValue);
                            case "Set-Cookie" -> maskCookieHeaderValue(headerValue);
                            default -> headerValue;
                        };
                        return String.format("[%s: %s]", headerKey, headerValue);
                    })
                    .collect(joining(", "));
        }

        private static String maskHeaderValue(String apiKeyHeaderValue) {
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

        private static String maskCookieHeaderValue(String cookieHeaderValue) {
            try {
                if (cookieHeaderValue.length() <= 4) {
                    return cookieHeaderValue;
                }
                return cookieHeaderValue.substring(0, 2)
                        + "..."
                        + cookieHeaderValue.substring(cookieHeaderValue.length() - 2);
            } catch (Exception e) {
                return "Failed to mask the cookie value.";
            }
        }
    }

    class ApiMetadata {

        @HeaderParam("Authorization")
        public final String authorization;

        @HeaderParam("api-key")
        public final String azureApiKey;
        @QueryParam("api-version")
        public final String apiVersion;

        @HeaderParam("OpenAI-Organization")
        public final String organizationId;

        private ApiMetadata(
                String openaiApiKey,
                String azureApiKey,
                String azureAdToken,
                String apiVersion,
                String organizationId) {
            if (azureAdToken != null) {
                this.authorization = "Bearer " + azureAdToken;
            } else if (openaiApiKey != null) {
                this.authorization = "Bearer " + openaiApiKey;
            } else {
                this.authorization = null;
            }

            this.azureApiKey = azureApiKey;
            this.apiVersion = apiVersion;
            this.organizationId = organizationId;
        }

        public static ApiMetadata.Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String azureApiKey;
            private String azureAdToken;
            private String openAiApiKey;
            private String apiVersion;
            private String organizationId;

            public ApiMetadata build() {
                if (azureAdToken != null) {
                    return new ApiMetadata(null, null, azureAdToken, apiVersion, organizationId);
                } else if (azureApiKey != null) {
                    return new ApiMetadata(null, azureApiKey, null, apiVersion, organizationId);
                } else if (openAiApiKey != null) {
                    return new ApiMetadata(openAiApiKey, null, null, apiVersion, organizationId);
                }

                return new ApiMetadata(null, null, null, apiVersion, organizationId);
            }

            public ApiMetadata.Builder azureAdToken(String azureAdToken) {
                this.azureAdToken = azureAdToken;
                return this;
            }

            public ApiMetadata.Builder azureApiKey(String azureApiKey) {
                this.azureApiKey = azureApiKey;
                return this;
            }

            public ApiMetadata.Builder openAiApiKey(String openAiApiKey) {
                this.openAiApiKey = openAiApiKey;
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

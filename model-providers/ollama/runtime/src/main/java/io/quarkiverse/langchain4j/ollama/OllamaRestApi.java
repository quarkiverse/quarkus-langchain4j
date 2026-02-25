package io.quarkiverse.langchain4j.ollama;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.client.api.ClientLogger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.runtime.CurlRequestLogger;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

/**
 * This Microprofile REST client is used as the building block of all the API calls to HuggingFace.
 * The implementation is provided by the Reactive REST Client in Quarkus.
 */
@Path("")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RegisterProvider(OllamaRestApi.OllamaRestApiReaderInterceptor.class)
@RegisterProvider(OllamaRestApi.OpenAiRestApiWriterInterceptor.class)
public interface OllamaRestApi {

    @Path("/api/chat")
    @POST
    ChatResponse chat(ChatRequest request);

    @Path("/api/chat")
    @POST
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<ChatResponse> streamingChat(ChatRequest request);

    @Path("/api/embed")
    @POST
    EmbeddingResponse embeddings(EmbeddingRequest request);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;
    }

    /**
     * This is needed because for some reason Vert.x is not giving us the entire content of the last chunk and this results
     * in a json parsing exception.
     */
    class OllamaRestApiReaderInterceptor implements ReaderInterceptor {

        @Override
        public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
            try {
                return context.proceed();
            } catch (ClientWebApplicationException | ProcessingException e) {
                // Depending on the Quarkus version MismatchedInputException could be wrapped in ProcessingException
                // or in WebApplicationException with Status 400.
                if ((e instanceof ProcessingException pe && pe.getCause() instanceof MismatchedInputException) ||
                        (e instanceof WebApplicationException wae
                                && ((wae.getCause() instanceof JsonParseException && wae.getResponse().getStatus() == 200) ||
                                        (wae.getCause() instanceof MismatchedInputException
                                                && wae.getResponse().getStatus() == 400)))) {
                    Object invokedMethod = context.getProperty("org.eclipse.microprofile.rest.client.invokedMethod");
                    if ((invokedMethod != null) && invokedMethod.toString().contains("OllamaRestApi.streamingChat")) {
                        InputStream is = context.getInputStream();
                        if (is instanceof ByteArrayInputStream bis) {
                            bis.reset();
                            String chunk = new String(bis.readAllBytes());
                            final var ctx = Vertx.currentContext();
                            if (ctx == null) {
                                throw e;
                            }

                            // This piece of code deals with is the case where a message from Ollama is not received as an entire line
                            // but in pieces (my guess is that it is a Vertx bug).
                            // There is nothing we can do in this case except for returning empty responses and in the meantime buffer the pieces
                            // by storing them in the Vertx Duplicated Context
                            String existingBuffer = ctx.getLocal("buffer");
                            if ((existingBuffer != null) && !existingBuffer.isEmpty()) {
                                if (chunk.endsWith("}")) {
                                    ctx.putLocal("buffer", "");
                                    String entireLine = existingBuffer + chunk;
                                    return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER.readValue(entireLine,
                                            ChatResponse.class);
                                } else {
                                    ctx.putLocal("buffer", existingBuffer + chunk);
                                    return ChatResponse.emptyNotDone();
                                }
                            } else {
                                ctx.putLocal("buffer", chunk);
                                return ChatResponse.emptyNotDone();
                            }
                        }
                    }
                }
                throw e;
            }
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
            if (entity instanceof ChatRequest request) {
                MultivaluedMap<String, Object> headers = context.getHeaders();
                List<Object> acceptList = headers.get(HttpHeaders.ACCEPT);
                if ((acceptList != null) && (acceptList.size() == 1)) {
                    String accept = (String) acceptList.get(0);
                    if (MediaType.APPLICATION_JSON.equals(accept)) {
                        if (Boolean.TRUE.equals(request.stream())) {
                            context.setEntity(ChatRequest.builder().from(request).stream(null).build());
                        }
                    } else if (MediaType.SERVER_SENT_EVENTS.equals(accept)) {
                        if (!Boolean.TRUE.equals(request.stream())) {
                            context.setEntity(ChatRequest.builder().from(request).stream(true).build());
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
    class OllamaLogger implements ClientLogger {
        private static final Logger log = Logger.getLogger(OllamaLogger.class);

        private final boolean logRequests;
        private final boolean logResponses;
        private final boolean logCurl;

        public OllamaLogger(boolean logRequests, boolean logResponses) {
            this(logRequests, logResponses, false);
        }

        public OllamaLogger(boolean logRequests, boolean logResponses, boolean logCurl) {
            this.logRequests = logRequests;
            this.logResponses = logResponses;
            this.logCurl = logCurl;
        }

        @Override
        public void setBodySize(int bodySize) {
            // ignore
        }

        @Override
        public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
            if (logRequests && log.isInfoEnabled()) {
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
            if (logCurl) {
                CurlRequestLogger.logCurl(log, request, body);
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
            String rawBody = body.toString();
            try {
                return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER.readTree(rawBody).toPrettyString();
            } catch (JsonProcessingException ignored) {
                return rawBody;
            }
        }

        private String inOneLine(MultiMap headers) {

            return stream(headers.spliterator(), false)
                    .map(header -> {
                        String headerKey = header.getKey();
                        String headerValue = header.getValue();
                        return String.format("[%s: %s]", headerKey, headerValue);
                    })
                    .collect(joining(", "));
        }
    }
}

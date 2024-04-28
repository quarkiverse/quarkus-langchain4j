package io.quarkiverse.langchain4j.ollama;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.ClientLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
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
public interface OllamaRestApi {

    @Path("/api/generate")
    @POST
    CompletionResponse generate(CompletionRequest request);

    @Path("/api/generate")
    @POST
    Multi<CompletionResponse> streamingGenerate(CompletionRequest request);

    @Path("/api/embeddings")
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
            } catch (Exception e) {
                InputStream is = context.getInputStream();
                if (is instanceof ByteArrayInputStream bis) {
                    bis.reset();
                    String chunk = new String(bis.readAllBytes());
                    final var ctx = Vertx.currentContext();
                    if (ctx == null) {
                        throw e;
                    }

                    // This piece of code deals with is the case where the last message from Ollama is not sent as entire line
                    // but in pieces. There is nothing we can do in this case except for returning empty responses.
                    // We have to keep track of when "done": true has been recorded in order to make sure that subsequent pieces
                    // are dealt with instead of throwing an exception. We keep track of this by using Vert.x duplicated context

                    if (chunk.contains("\"done\":true")) {
                        ctx.putLocal("done", true);
                        return doneResponse();
                    } else {
                        if (Boolean.TRUE.equals(ctx.getLocal("done"))) {
                            return doneResponse();
                        }
                    }
                }
                throw e;
            }
        }

        private CompletionResponse doneResponse() {
            return CompletionResponse.builder().response("").done(true).build();
        }
    }

    /**
     * Introduce a custom logger as the stock one logs at the DEBUG level by default...
     */
    class OllamaLogger implements ClientLogger {
        private static final Logger log = Logger.getLogger(OllamaLogger.class);

        private final boolean logRequests;
        private final boolean logResponses;

        public OllamaLogger(boolean logRequests, boolean logResponses) {
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
                        return String.format("[%s: %s]", headerKey, headerValue);
                    })
                    .collect(joining(", "));
        }
    }
}

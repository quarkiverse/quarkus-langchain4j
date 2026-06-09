package io.quarkiverse.langchain4j.agentic.runtime.a2a;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.a2aproject.sdk.client.http.ServerSentEventParser;
import org.a2aproject.sdk.common.A2AErrorMessages;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;

/**
 * Vert.x-based implementation of {@link A2AHttpClient}.
 * <p>
 * Uses {@link io.vertx.core.http.HttpClient} from Vert.x core for both synchronous
 * requests and asynchronous SSE streaming. This integrates properly with Quarkus's
 * managed Vert.x event loop and connection pool, avoiding the creation of a separate
 * JDK {@code HttpClient} instance.
 * <p>
 * For synchronous operations, requests are executed on the Vert.x event loop and the
 * calling thread blocks via {@link Future#toCompletionStage()}. For SSE streaming,
 * the response body is streamed through a {@link ServerSentEventParser} which handles
 * the SSE protocol parsing.
 */
public class VertxA2AHttpClient implements A2AHttpClient {

    private final Vertx vertx;
    private final io.vertx.core.http.HttpClient httpClient;

    public VertxA2AHttpClient(Vertx vertx) {
        this.vertx = vertx;
        this.httpClient = vertx.createHttpClient();
    }

    @Override
    public GetBuilder createGet() {
        return new VertxGetBuilder();
    }

    @Override
    public PostBuilder createPost() {
        return new VertxPostBuilder();
    }

    @Override
    public DeleteBuilder createDelete() {
        return new VertxDeleteBuilder();
    }

    /**
     * Base builder that accumulates URL and headers, then delegates to subclasses
     * for method-specific request construction.
     * <p>
     * Builders are single-use and not thread-safe — configure and execute from
     * a single thread, matching JDK {@code HttpRequest.Builder} conventions.
     */
    private abstract class VertxBuilder<T extends Builder<T>> implements Builder<T> {
        protected String url = "";
        protected final Map<String, String> headers = new HashMap<>();

        @Override
        public T url(String url) {
            this.url = url;
            return self();
        }

        @Override
        public T addHeader(String name, String value) {
            headers.put(name, value);
            return self();
        }

        @Override
        public T addHeaders(Map<String, String> headers) {
            if (headers != null && !headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    addHeader(entry.getKey(), entry.getValue());
                }
            }
            return self();
        }

        @SuppressWarnings("unchecked")
        T self() {
            return (T) this;
        }

        /**
         * Creates request options with the configured URL and headers.
         */
        protected RequestOptions createRequestOptions(HttpMethod method) {
            RequestOptions options = new RequestOptions()
                    .setMethod(method)
                    .setAbsoluteURI(url)
                    .setFollowRedirects(true);
            return options;
        }

        /**
         * Applies accumulated headers to an {@link HttpClientRequest}.
         */
        protected void applyHeaders(HttpClientRequest request) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.putHeader(entry.getKey(), entry.getValue());
            }
        }

        /**
         * Executes a synchronous request by blocking on the Vert.x Future.
         * Throws {@link IOException} for auth failures (matching JDK behaviour).
         */
        protected A2AHttpResponse executeSync(HttpMethod method, Buffer body) throws IOException, InterruptedException {
            try {
                CompletableFuture<A2AHttpResponse> cf = httpClient.request(createRequestOptions(method))
                        .compose(req -> {
                            applyHeaders(req);
                            Future<HttpClientResponse> responseFuture;
                            if (body != null) {
                                responseFuture = req.send(body);
                            } else {
                                responseFuture = req.send();
                            }
                            return responseFuture.compose(resp -> resp.body().map(buf -> {
                                int status = resp.statusCode();
                                String responseBody = buf != null ? buf.toString() : "";
                                return (A2AHttpResponse) new VertxHttpResponse(status, responseBody);
                            }));
                        })
                        .toCompletionStage()
                        .toCompletableFuture();
                A2AHttpResponse response = cf.get();
                if (response.status() == HTTP_UNAUTHORIZED) {
                    throw new IOException(A2AErrorMessages.AUTHENTICATION_FAILED);
                } else if (response.status() == HTTP_FORBIDDEN) {
                    throw new IOException(A2AErrorMessages.AUTHORIZATION_FAILED);
                }
                return response;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException ioe) {
                    throw ioe;
                }
                throw new IOException("Request failed", cause);
            }
        }

        /**
         * Executes an async SSE streaming request.
         * <p>
         * Mirrors the JDK implementation's dual-mode behaviour: if the response has
         * {@code Content-Type: text/event-stream} and a 2xx status, lines are fed through
         * {@link ServerSentEventParser}. Otherwise, the entire body is delivered as a
         * single {@link ServerSentEvent}.
         */
        protected CompletableFuture<Void> executeAsyncSSE(
                HttpMethod method,
                Buffer body,
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) {
            CompletableFuture<Void> result = new CompletableFuture<>();

            httpClient.request(createRequestOptions(method))
                    .onFailure(err -> {
                        errorConsumer.accept(err);
                        result.complete(null);
                    })
                    .onSuccess(req -> {
                        applyHeaders(req);
                        req.putHeader(ACCEPT, EVENT_STREAM);

                        Future<HttpClientResponse> responseFuture;
                        if (body != null) {
                            responseFuture = req.send(body);
                        } else {
                            responseFuture = req.send();
                        }

                        responseFuture
                                .onFailure(err -> {
                                    errorConsumer.accept(err);
                                    result.complete(null);
                                })
                                .onSuccess(resp -> {
                                    int statusCode = resp.statusCode();

                                    // Auth/authz errors — signal immediately
                                    if (statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN) {
                                        String errorMsg = statusCode == HTTP_UNAUTHORIZED
                                                ? A2AErrorMessages.AUTHENTICATION_FAILED
                                                : A2AErrorMessages.AUTHORIZATION_FAILED;
                                        errorConsumer.accept(new IOException(errorMsg));
                                        result.complete(null);
                                        return;
                                    }

                                    String contentType = resp.getHeader("Content-Type");
                                    boolean isSse = isSuccess(statusCode)
                                            && contentType != null
                                            && contentType.contains(EVENT_STREAM);

                                    if (isSse) {
                                        handleSseResponse(resp, messageConsumer, errorConsumer,
                                                completeRunnable, result);
                                    } else {
                                        handleNonSseResponse(resp, messageConsumer, errorConsumer,
                                                completeRunnable, result);
                                    }
                                });
                    });

            return result;
        }

        private void handleSseResponse(
                HttpClientResponse resp,
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable,
                CompletableFuture<Void> result) {
            ServerSentEventParser parser = new ServerSentEventParser(messageConsumer, errorConsumer);
            // Accumulates partial lines across Buffer chunks
            StringBuilder lineBuffer = new StringBuilder();

            resp.handler(buffer -> {
                String chunk = buffer.toString();
                for (int i = 0; i < chunk.length(); i++) {
                    char c = chunk.charAt(i);
                    if (c == '\n') {
                        parser.processLine(lineBuffer.toString());
                        lineBuffer.setLength(0);
                    } else if (c == '\r') {
                        parser.processLine(lineBuffer.toString());
                        lineBuffer.setLength(0);
                        // Skip \n if it follows \r (CRLF)
                        if (i + 1 < chunk.length() && chunk.charAt(i + 1) == '\n') {
                            i++;
                        }
                    } else {
                        lineBuffer.append(c);
                    }
                }
            });

            resp.exceptionHandler(err -> {
                errorConsumer.accept(err);
                result.complete(null);
            });

            resp.endHandler(v -> {
                // Flush any remaining partial line
                if (lineBuffer.length() > 0) {
                    parser.processLine(lineBuffer.toString());
                }
                parser.flush();
                completeRunnable.run();
                result.complete(null);
            });
        }

        private void handleNonSseResponse(
                HttpClientResponse resp,
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable,
                CompletableFuture<Void> result) {
            resp.body()
                    .onSuccess(buf -> {
                        String responseBody = buf != null ? buf.toString() : "";
                        if (!responseBody.isEmpty()) {
                            messageConsumer.accept(new ServerSentEvent(responseBody));
                        }
                        completeRunnable.run();
                        result.complete(null);
                    })
                    .onFailure(err -> {
                        errorConsumer.accept(err);
                        result.complete(null);
                    });
        }
    }

    private class VertxGetBuilder extends VertxBuilder<GetBuilder> implements GetBuilder {

        @Override
        public A2AHttpResponse get() throws IOException, InterruptedException {
            return executeSync(HttpMethod.GET, null);
        }

        @Override
        public CompletableFuture<Void> getAsyncSSE(
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) {
            return executeAsyncSSE(HttpMethod.GET, null, messageConsumer, errorConsumer, completeRunnable);
        }
    }

    private class VertxPostBuilder extends VertxBuilder<PostBuilder> implements PostBuilder {
        private String body = "";

        @Override
        public PostBuilder body(String body) {
            this.body = body;
            return self();
        }

        @Override
        public A2AHttpResponse post() throws IOException, InterruptedException {
            return executeSync(HttpMethod.POST, Buffer.buffer(body));
        }

        @Override
        public CompletableFuture<Void> postAsyncSSE(
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) {
            return executeAsyncSSE(HttpMethod.POST, Buffer.buffer(body),
                    messageConsumer, errorConsumer, completeRunnable);
        }
    }

    private class VertxDeleteBuilder extends VertxBuilder<DeleteBuilder> implements DeleteBuilder {

        @Override
        public A2AHttpResponse delete() throws IOException, InterruptedException {
            return executeSync(HttpMethod.DELETE, null);
        }
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= HTTP_OK && statusCode < HTTP_MULT_CHOICE;
    }

    private record VertxHttpResponse(int status, String body) implements A2AHttpResponse {

        @Override
        public boolean success() {
            return isSuccess(status);
        }
    }
}

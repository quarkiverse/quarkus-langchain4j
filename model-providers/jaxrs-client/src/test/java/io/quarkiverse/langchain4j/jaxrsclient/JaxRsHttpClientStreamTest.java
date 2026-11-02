package io.quarkiverse.langchain4j.jaxrsclient;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import io.quarkiverse.langchain4j.QuarkusHttpException;
import io.quarkus.runtime.BlockingOperationControl;
import io.quarkus.runtime.IOThreadDetector;

/**
 * {@link JaxRsHttpClient#stream(HttpRequest, ServerSentEventParser)} must hand the body to the caller's parser
 * untouched whatever its content type, because a caller such as the MCP Streamable HTTP transport only learns from the
 * response headers whether the server answered with a single JSON document or an event stream.
 */
class JaxRsHttpClientStreamTest {

    private HttpServer server;
    private String baseUrl;
    private HttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        BlockingOperationControl.setIoThreadDetector(new IOThreadDetector[0]);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        client = new JaxRsHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void jsonBodyIsPassedToTheParserUnchanged() throws InterruptedException {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";
        respondWith("/json", 200, "application/json", json);

        Recorder recorder = stream(POST, "/json", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");

        assertEquals(null, recorder.error.get());
        HttpResponseReceived response = assertInstanceOf(HttpResponseReceived.class, recorder.events.get(0));
        assertEquals(200, response.response().statusCode());
        assertEquals("application/json", response.response().contentType());
        assertEquals(json, recorder.body());
    }

    @Test
    void eventStreamIsPassedToTheParserUnchangedAndKeepsItsContentType() throws InterruptedException {
        // RESTEasy would otherwise parse the events itself and drop the fields it does not expose, such as retry
        String events = "id: 7\nretry: 1000\nevent: message\ndata: {\"a\":1}\n\n: comment\ndata: line1\ndata: line2\n\n";
        respondWith("/sse", 200, "text/event-stream", events);

        Recorder recorder = stream(GET, "/sse", null);

        assertEquals(null, recorder.error.get());
        HttpResponseReceived response = assertInstanceOf(HttpResponseReceived.class, recorder.events.get(0));
        assertEquals("text/event-stream", response.response().contentType());
        assertEquals(events, recorder.body());
    }

    @Test
    void newlineDelimitedJsonIsPassedToTheParserUnchanged() throws InterruptedException {
        String body = "{\"a\":1}\n{\"b\":2}\n";
        respondWith("/ndjson", 200, "application/x-ndjson", body);

        Recorder recorder = stream(GET, "/ndjson", null);

        assertEquals(null, recorder.error.get());
        assertEquals(body, recorder.body());
    }

    @Test
    void acceptedWithoutBodyCompletesAfterTheResponse() throws InterruptedException {
        server.createContext("/accepted", exchange -> {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();

        Recorder recorder = stream(POST, "/accepted", "{}");

        assertEquals(null, recorder.error.get());
        assertEquals(1, recorder.events.size());
        assertEquals(202, ((HttpResponseReceived) recorder.events.get(0)).response().statusCode());
    }

    @Test
    void errorStatusIsReportedAndFailsWithTheBody() throws InterruptedException {
        server.createContext("/error", exchange -> {
            byte[] body = "bad-request".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Retry-After", "7");
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        Recorder recorder = stream(GET, "/error", null);

        HttpErrorResponseReceived errorResponse = assertInstanceOf(HttpErrorResponseReceived.class,
                recorder.events.get(0));
        assertEquals(400, errorResponse.statusCode());
        QuarkusHttpException exception = assertInstanceOf(QuarkusHttpException.class, recorder.error.get());
        assertEquals(400, exception.statusCode());
        assertEquals("bad-request", exception.getMessage());
        assertEquals(Optional.of("7"), exception.header("retry-after"));
    }

    @Test
    void eventsArriveBeforeTheResponseEnds() throws InterruptedException {
        CountDownLatch firstChunkReceived = new CountDownLatch(1);
        server.createContext("/slow", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("data: first\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                // the server only finishes once the client has seen the first event
                firstChunkReceived.await(5, TimeUnit.SECONDS);
                os.write("data: second\n\n".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        Recorder recorder = new Recorder() {
            @Override
            public void onNext(HttpStreamingEvent event) {
                super.onNext(event);
                if (event instanceof ServerSentEvent) {
                    firstChunkReceived.countDown();
                }
            }
        };
        client.stream(request(GET, "/slow", null), new ChunkParser()).subscribe(recorder);

        assertTrue(firstChunkReceived.await(5, TimeUnit.SECONDS), "the first event should arrive while streaming");
        assertTrue(recorder.done.await(5, TimeUnit.SECONDS));
        assertEquals(null, recorder.error.get());
        assertEquals("data: first\n\ndata: second\n\n", recorder.body());
    }

    @Test
    void cancellingTheSubscriptionStopsTheStream() throws InterruptedException {
        CountDownLatch serverWriteFailed = new CountDownLatch(1);
        server.createContext("/endless", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                while (true) {
                    os.write("data: tick\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(50);
                }
            } catch (IOException e) {
                serverWriteFailed.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        CountDownLatch firstEvent = new CountDownLatch(1);
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        client.stream(request(GET, "/endless", null), new ChunkParser()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscription.set(s);
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(HttpStreamingEvent event) {
                if (event instanceof ServerSentEvent) {
                    firstEvent.countDown();
                }
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        assertTrue(firstEvent.await(5, TimeUnit.SECONDS));
        subscription.get().cancel();
        assertTrue(serverWriteFailed.await(5, TimeUnit.SECONDS), "cancelling should close the connection");
    }

    private void respondWith(String path, int status, String contentType, String body) {
        server.createContext(path, exchange -> respond(exchange, status, contentType, body));
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private HttpRequest request(dev.langchain4j.http.client.HttpMethod method, String path, String body) {
        return HttpRequest.builder()
                .method(method)
                .url(baseUrl + path)
                .body(body)
                .build();
    }

    private Recorder stream(dev.langchain4j.http.client.HttpMethod method, String path, String body)
            throws InterruptedException {
        Recorder recorder = new Recorder();
        client.stream(request(method, path, body), new ChunkParser()).subscribe(recorder);
        assertTrue(recorder.done.await(5, TimeUnit.SECONDS), "the stream should terminate");
        return recorder;
    }

    /**
     * Turns every chunk of the body into an event, so that the test can reassemble the body exactly as it arrived.
     */
    private static class ChunkParser implements ServerSentEventParser {

        @Override
        public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Incremental incremental() {
            return new Incremental() {
                @Override
                public List<ServerSentEvent> feed(ByteBuffer bytes) {
                    return List.of(new ServerSentEvent(null, StandardCharsets.UTF_8.decode(bytes).toString()));
                }

                @Override
                public List<ServerSentEvent> flush() {
                    return List.of();
                }
            };
        }
    }

    private static class Recorder implements Flow.Subscriber<HttpStreamingEvent> {

        final List<HttpStreamingEvent> events = new CopyOnWriteArrayList<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(HttpStreamingEvent event) {
            events.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

        String body() {
            return events.stream()
                    .filter(ServerSentEvent.class::isInstance)
                    .map(event -> ((ServerSentEvent) event).data())
                    .collect(Collectors.joining());
        }
    }
}

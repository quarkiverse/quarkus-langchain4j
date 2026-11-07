package io.quarkiverse.langchain4j.jaxrsclient;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import io.quarkus.runtime.BlockingOperationControl;
import io.quarkus.runtime.IOThreadDetector;

class JaxRsHttpClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        BlockingOperationControl.setIoThreadDetector(new IOThreadDetector[0]);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void executeShouldReturnSuccessfulResponse() {
        server.createContext("/ok", this::handleOk);
        server.start();

        HttpClient client = new JaxRsHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url(baseUrl + "/ok")
                .addHeader("X-Test", "value-1", "value-2")
                .build();

        SuccessfulHttpResponse response = client.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals("ok-body", response.body());
        assertEquals(List.of("present"), response.headers().get("x-server-header"));
    }

    @Test
    void executeShouldThrowHttpExceptionForErrorResponse() {
        server.createContext("/error", this::handleError);
        server.start();

        HttpClient client = new JaxRsHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url(baseUrl + "/error")
                .build();

        HttpException exception = assertThrows(HttpException.class, () -> client.execute(request));

        assertEquals(400, exception.statusCode());
        assertEquals("bad-request", exception.getMessage());
    }

    @Test
    void executeSseShouldStreamEvents() throws InterruptedException {
        server.createContext("/sse", this::handleSse);
        server.start();

        HttpClient client = new JaxRsHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url(baseUrl + "/sse")
                .build();

        List<ServerSentEvent> events = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean(false);
        CountDownLatch finished = new CountDownLatch(1);

        ServerSentEventListener listener = new ServerSentEventListener() {
            @Override
            public void onEvent(ServerSentEvent event) {
                events.add(event);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }

            @Override
            public void onClose() {
                closed.set(true);
                finished.countDown();
            }
        };

        client.execute(request, listener);
        boolean completed = finished.await(3, TimeUnit.SECONDS);

        assertEquals(true, completed);
        assertEquals(null, error.get());
        assertEquals(true, closed.get());
        assertEquals(1, events.size());
        assertEquals("hello", events.get(0).data());
    }

    @Test
    void executeSseShouldDeliverEventsBeforeResponseCompletes() throws InterruptedException {
        // The server sends the first event immediately, then holds the connection open
        // for 5 seconds before sending the second event.
        // If the implementation buffers the entire response, no events arrive until
        // after the 5-second hold — the 2-second latch timeout will expire.
        // With true streaming, the first event arrives immediately.
        CountDownLatch serverDone = new CountDownLatch(1);
        server.createContext("/sse-slow", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("data: first\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                Thread.sleep(5000);
                os.write("data: second\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                serverDone.countDown();
            }
        });
        server.start();

        HttpClient client = new JaxRsHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url(baseUrl + "/sse-slow")
                .build();

        CountDownLatch firstEventReceived = new CountDownLatch(1);
        List<String> receivedData = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        ServerSentEventListener listener = new ServerSentEventListener() {
            @Override
            public void onEvent(ServerSentEvent event) {
                receivedData.add(event.data());
                firstEventReceived.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }

            @Override
            public void onClose() {
            }
        };

        client.execute(request, listener);

        // The first event must arrive within 2 seconds.
        // With buffering this would fail because the server holds the connection for 5s.
        boolean arrived = firstEventReceived.await(2, TimeUnit.SECONDS);

        assertEquals(true, arrived, "First event should arrive before the server finishes responding");
        assertEquals(null, error.get(), "No errors expected");
        assertEquals("first", receivedData.get(0));

        // The server is still holding the connection open at this point
        assertEquals(false, serverDone.await(0, TimeUnit.SECONDS),
                "Server should still be sending when the first event arrives");
    }

    private void handleOk(HttpExchange exchange) throws IOException {
        byte[] responseBody = "ok-body".getBytes();
        exchange.getResponseHeaders().add("X-Server-Header", "present");
        exchange.sendResponseHeaders(200, responseBody.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBody);
        } finally {
            exchange.close();
        }
    }

    private void handleError(HttpExchange exchange) throws IOException {
        byte[] responseBody = "bad-request".getBytes();
        exchange.sendResponseHeaders(400, responseBody.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBody);
        } finally {
            exchange.close();
        }
    }

    private void handleSse(HttpExchange exchange) throws IOException {
        byte[] responseBody = "data: hello\n\n".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, responseBody.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBody);
        } finally {
            exchange.close();
        }
    }

}

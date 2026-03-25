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
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
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
    void executeSseShouldCallOpenParseAndClose() throws InterruptedException {
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

        AtomicReference<SuccessfulHttpResponse> openResponse = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<String> parsedPayload = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean(false);
        CountDownLatch finished = new CountDownLatch(1);

        ServerSentEventListener listener = new ServerSentEventListener() {
            @Override
            public void onOpen(SuccessfulHttpResponse response) {
                openResponse.set(response);
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

        ServerSentEventParser parser = (inputStream, ignored) -> {
            try {
                parsedPayload.set(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        client.execute(request, parser, listener);
        boolean completed = finished.await(3, TimeUnit.SECONDS);

        assertEquals(true, completed);
        assertEquals(null, error.get());
        assertEquals(true, closed.get());
        assertEquals(200, openResponse.get().statusCode());
        assertEquals(null, openResponse.get().body());
        assertEquals("data: hello\n\n", parsedPayload.get());
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

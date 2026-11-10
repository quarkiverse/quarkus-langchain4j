package io.quarkiverse.langchain4j.agentic.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A fake A2A agent named "Secured Agent", which answers "pong" and records the {@code Authorization} header of each
 * A2A request.
 */
final class StubA2AAgent implements AutoCloseable {

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpServer server;
    private final List<String> receivedAuthorization = new CopyOnWriteArrayList<>();

    StubA2AAgent() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    // The Authorization header of each A2A request, "null" when there was none
    List<String> receivedAuthorization() {
        return receivedAuthorization;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getPath().equals("/.well-known/agent-card.json")) {
            respond(exchange, """
                    {
                      "name": "Secured Agent",
                      "description": "An agent that requires a token",
                      "version": "1.0.0",
                      "supportedInterfaces": [
                        { "url": "%s", "protocolBinding": "JSONRPC", "protocolVersion": "1.0" }
                      ],
                      "capabilities": {},
                      "skills": [ { "id": "s", "name": "S", "description": "A skill", "tags": ["t"] } ],
                      "defaultInputModes": ["text"],
                      "defaultOutputModes": ["text"]
                    }
                    """.formatted(url()));
            return;
        }
        receivedAuthorization.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher id = ID.matcher(body);
        respond(exchange, """
                {"jsonrpc":"2.0","id":"%s","result":{"message":{"messageId":"reply","role":"ROLE_AGENT",\
                "parts":[{"text":"pong"}]}}}""".formatted(id.find() ? id.group(1) : ""));
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

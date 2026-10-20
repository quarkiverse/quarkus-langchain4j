package io.quarkiverse.langchain4j.mockapi.internal.server;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;

/**
 * The mock model API HTTP server: the provider endpoints the dialects serve plus the admin API, on a Vert.x instance
 * with a single event loop that this class owns.
 */
public final class MockApiServer implements AutoCloseable {

    private final OptionalInt port;
    private final String defaultChatReply;
    private final int defaultEmbeddingDimension;
    private final DialectRegistry registry;

    private Vertx vertx;
    private HttpServer server;
    private MockState state;

    public MockApiServer(OptionalInt port, String defaultChatReply, int defaultEmbeddingDimension,
            DialectRegistry registry) {
        this.port = port;
        this.defaultChatReply = defaultChatReply;
        this.defaultEmbeddingDimension = defaultEmbeddingDimension;
        this.registry = registry;
    }

    public void start() {
        Map<Operation, Reply> defaults = new EnumMap<>(Operation.class);
        defaults.put(Operation.CHAT, ChatReply.text(defaultChatReply));
        defaults.put(Operation.EMBEDDINGS, EmbeddingReply.derived(defaultEmbeddingDimension));
        defaults.put(Operation.MODERATION, ModerationReply.notFlagged());
        defaults.put(Operation.SCORING, ScoringReply.equalScores());
        state = new MockState(defaults);
        vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1));
        ModelApiHandler model = new ModelApiHandler(state, registry, vertx);
        AdminApiHandler admin = new AdminApiHandler(state, registry);
        server = vertx.createHttpServer();
        server.requestHandler(req -> {
            if (admin.handles(req.path())) {
                admin.handle(req);
            } else {
                model.handle(req);
            }
        });
        try {
            Future.await(server.listen(port.orElse(0), "localhost"));
        } catch (Exception e) {
            Future.await(vertx.close());
            vertx = null;
            server = null;
            throw new IllegalStateException("The mock model API could not listen on port " + port.orElse(0)
                    + " (quarkus.langchain4j.mock-api.port); is it already in use?", e);
        }
    }

    int port() {
        return server.actualPort();
    }

    Vertx vertx() {
        return vertx;
    }

    public String url() {
        return "http://localhost:" + port();
    }

    @Override
    public void close() {
        if (server != null) {
            Future.await(server.close());
            server = null;
        }
        if (vertx != null) {
            Future.await(vertx.close());
            vertx = null;
        }
    }
}

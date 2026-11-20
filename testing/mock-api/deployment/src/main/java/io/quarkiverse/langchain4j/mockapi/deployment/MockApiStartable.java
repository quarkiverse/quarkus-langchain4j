package io.quarkiverse.langchain4j.mockapi.internal.server;

import java.util.OptionalInt;

import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkus.deployment.builditem.Startable;

public final class MockApiStartable implements Startable {

    private final MockApiServer server;

    public MockApiStartable(OptionalInt port, String defaultChatReply, int defaultEmbeddingDimension,
            DialectRegistry registry) {
        this.server = new MockApiServer(port, defaultChatReply, defaultEmbeddingDimension, registry);
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public String getConnectionInfo() {
        return server.url();
    }

    @Override
    public String getContainerId() {
        return null;
    }

    @Override
    public void close() {
        server.close();
    }
}

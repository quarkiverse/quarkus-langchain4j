package io.quarkiverse.langchain4j.mockapi.internal;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.internal.config.MockApiRuntimeConfig;
import io.vertx.core.Vertx;

@Singleton
public class MockModelServerProducer {

    @Produces
    @Singleton
    MockModelServer mock(Vertx vertx, MockApiRuntimeConfig cfg) {
        return MockModelServer.connect(vertx, cfg.url().orElseThrow(
                () -> new IllegalStateException(
                        "quarkus.langchain4j.mock-api.url not set: is the mock API dev service running?")));
    }

    void dispose(@Disposes MockModelServer m) {
        m.close();
    }
}

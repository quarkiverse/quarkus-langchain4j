package io.quarkiverse.langchain4j.agentic.runtime.a2a;

import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClientProvider;

import io.quarkus.arc.Arc;
import io.vertx.core.Vertx;

/**
 * Quarkus-specific {@link A2AHttpClientProvider} that creates {@link VertxA2AHttpClient}
 * instances backed by the CDI-managed Vert.x instance.
 * <p>
 * This provider replaces the default JDK {@code HttpClient}-based implementation with
 * Vert.x, which integrates properly with Quarkus's event loop and connection pooling.
 * <p>
 * <b>Lifecycle note:</b> the constructor does nothing — the provider is instantiated during
 * {@code A2AHttpClientFactory} static init, potentially before CDI is wired. The
 * {@link #create()} method resolves Vert.x lazily from Arc, which is safe because
 * {@code create()} is only called at agent creation time when CDI is fully available.
 */
public class VertxA2AHttpClientProvider implements A2AHttpClientProvider {

    @Override
    public A2AHttpClient create() {
        Vertx vertx = Arc.container().instance(Vertx.class).get();
        return new VertxA2AHttpClient(vertx);
    }

    @Override
    public int priority() {
        return 100; // Beats JdkA2AHttpClientProvider (priority 0)
    }

    @Override
    public String name() {
        return "vertx";
    }
}

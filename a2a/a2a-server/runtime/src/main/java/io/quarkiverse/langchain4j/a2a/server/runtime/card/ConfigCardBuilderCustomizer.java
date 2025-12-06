package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

import io.a2a.spec.AgentCard;
import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.runtime.config.A2AClientRuntimeConfiguration;
import io.quarkus.vertx.http.HttpServerStart;
import io.quarkus.vertx.http.HttpsServerStart;
import io.vertx.core.http.HttpServerOptions;

@ApplicationScoped
public class ConfigCardBuilderCustomizer implements AgentCardBuilderCustomizer {

    private final A2AClientRuntimeConfiguration configuration;
    private HttpServerOptions httpServerOptions;
    private HttpServerOptions httpsServerOptions;

    public ConfigCardBuilderCustomizer(A2AClientRuntimeConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void customize(AgentCard.Builder cardBuilder) {
        populateUrl(cardBuilder);
        cardBuilder.version(configuration.version());
    }

    private void populateUrl(AgentCard.Builder cardBuilder) {
        if (configuration.url().isPresent()) {
            cardBuilder.url(configuration.url().get());
        } else {
            // TODO: determine which port to use
            HttpServerOptions effectiveOptions = httpServerOptions;
            cardBuilder.url("http://%s:%d".formatted(effectiveOptions.getHost(), effectiveOptions.getPort()));
        }
    }

    void httpStarted(@ObservesAsync HttpServerStart start) {
        this.httpServerOptions = start.options();
    }

    void httpsStarted(@ObservesAsync HttpsServerStart start) {
        this.httpsServerOptions = start.options();
    }
}

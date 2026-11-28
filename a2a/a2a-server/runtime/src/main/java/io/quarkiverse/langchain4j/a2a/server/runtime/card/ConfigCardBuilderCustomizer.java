package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;

import io.quarkiverse.langchain4j.a2a.server.runtime.config.A2AServerRuntimeConfiguration;

/**
 * Fills a card with what the configuration says about the agent: its name, description and version, and the URL its
 * interfaces are reached at. Every agent card is filled this way before anything else contributes to it.
 */
@ApplicationScoped
public class ConfigCardBuilderCustomizer {

    private final A2AServerRuntimeConfiguration configuration;
    private final DetectedTransports detectedTransports;
    private final ServerAddress serverAddress;

    public ConfigCardBuilderCustomizer(A2AServerRuntimeConfiguration configuration,
            DetectedTransports detectedTransports, ServerAddress serverAddress) {
        this.configuration = configuration;
        this.detectedTransports = detectedTransports;
        this.serverAddress = serverAddress;
    }

    public void customize(AgentCard.Builder cardBuilder) {
        populateSupportedInterfaces(cardBuilder);
        cardBuilder
                .name(configuration.name())
                .description(configuration.description().orElse(""))
                .version(configuration.version());
    }

    private void populateSupportedInterfaces(AgentCard.Builder cardBuilder) {
        String url = determineUrl();
        List<AgentInterface> interfaces = new ArrayList<>();
        for (String protocolBinding : detectedTransports.getProtocolBindings()) {
            interfaces.add(new AgentInterface(protocolBinding, url));
        }
        cardBuilder.supportedInterfaces(interfaces);
    }

    /**
     * A URL from the configuration names the endpoint as clients reach it, so it is used as it is. The detected one is
     * the server's address followed by the HTTP root path, which is where the A2A routes are served.
     */
    private String determineUrl() {
        if (configuration.url().isPresent()) {
            return configuration.url().get();
        }
        return serverAddress.url() + detectedTransports.getRootPath();
    }
}

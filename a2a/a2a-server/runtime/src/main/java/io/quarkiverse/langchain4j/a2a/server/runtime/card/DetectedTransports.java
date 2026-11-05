package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.util.List;

/**
 * The A2A transports whose reference implementation was found on the classpath at build time.
 * <p>
 * Values are {@link org.a2aproject.sdk.spec.TransportProtocol#asString()} protocol bindings, ordered by preference:
 * the first one becomes the preferred interface of the Agent Card.
 */
public final class DetectedTransports {

    private final List<String> protocolBindings;

    public DetectedTransports(List<String> protocolBindings) {
        this.protocolBindings = protocolBindings;
    }

    public List<String> getProtocolBindings() {
        return protocolBindings;
    }
}

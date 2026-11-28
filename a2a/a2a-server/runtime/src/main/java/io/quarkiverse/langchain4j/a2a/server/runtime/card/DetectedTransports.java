package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.util.List;

/**
 * The A2A transports whose reference implementation was found on the classpath at build time, and the path their
 * HTTP routes are served under.
 * <p>
 * Values are {@link org.a2aproject.sdk.spec.TransportProtocol#asString()} protocol bindings, ordered by preference:
 * the first one becomes the preferred interface of the Agent Card.
 */
public final class DetectedTransports {

    private final List<String> protocolBindings;
    private final String rootPath;

    public DetectedTransports(List<String> protocolBindings, String rootPath) {
        this.protocolBindings = protocolBindings;
        this.rootPath = rootPath;
    }

    public List<String> getProtocolBindings() {
        return protocolBindings;
    }

    /**
     * The value of {@code quarkus.http.root-path} without its trailing slash, so empty when the routes are served at
     * the root of the server.
     */
    public String getRootPath() {
        return rootPath;
    }
}

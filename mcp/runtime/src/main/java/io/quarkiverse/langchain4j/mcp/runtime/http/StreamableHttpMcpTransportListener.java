package io.quarkiverse.langchain4j.mcp.runtime.http;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.inject.spi.CDI;

/**
 * Is notified about every Streamable HTTP MCP transport right before it is built, and can change what it is built
 * with.
 * <p>
 * Implementations are CDI beans. Every such bean is notified about every {@link QuarkusStreamableHttpMcpTransport}
 * created in the application, regardless of whether the MCP client is declared in configuration or the transport is
 * built programmatically using {@link QuarkusStreamableHttpMcpTransport.Builder}. When several listeners exist,
 * they are notified in the order of their bean priority, highest first.
 * <p>
 * A listener can read what the builder was given, see {@link QuarkusStreamableHttpMcpTransport.Builder#getUrl()}
 * and {@link QuarkusStreamableHttpMcpTransport.Builder#getMcpClientName()}, and change it using the regular builder
 * methods. A typical use case is a testing tool that needs the client to connect to a different address than the
 * one it was configured with.
 * <p>
 * The API is broadly similar to what the REST Client provides with
 * {@link org.eclipse.microprofile.rest.client.spi.RestClientBuilderListener}.
 */
public interface StreamableHttpMcpTransportListener {

    /**
     * Invoked once per transport, from {@link QuarkusStreamableHttpMcpTransport.Builder#build()}, on the thread
     * that builds the transport.
     *
     * @param builder the builder holding everything the transport is about to be built with
     */
    void onNewTransport(QuarkusStreamableHttpMcpTransport.Builder builder);

    static List<StreamableHttpMcpTransportListener> resolve() {
        List<StreamableHttpMcpTransportListener> listeners = new ArrayList<>();
        for (var handle : CDI.current().select(StreamableHttpMcpTransportListener.class).handles()) {
            listeners.add(handle.get());
        }
        return listeners;
    }
}

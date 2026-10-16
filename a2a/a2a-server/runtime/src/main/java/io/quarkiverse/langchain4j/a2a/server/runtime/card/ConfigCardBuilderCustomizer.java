package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.langchain4j.a2a.server.runtime.config.A2AServerRuntimeConfiguration;
import io.quarkiverse.langchain4j.a2a.server.runtime.config.A2AServerRuntimeConfiguration.TenantConfiguration;
import io.quarkus.vertx.http.HttpServerStart;
import io.quarkus.vertx.http.HttpsServerStart;
import io.vertx.core.http.HttpServerOptions;

/**
 * Fills a card with what the configuration says about the agent: its name, description and version, and the URL its
 * interfaces are reached at. Every agent card is filled this way before anything else contributes to it.
 */
@ApplicationScoped
public class ConfigCardBuilderCustomizer {

    private static final Set<String> WILDCARD_HOSTS = Set.of("0.0.0.0", "::", "::0", "0:0:0:0:0:0:0:0");
    private static final String HTTP_PORT_PROPERTY = "quarkus.http.port";
    private static final String HTTPS_PORT_PROPERTY = "quarkus.http.ssl-port";

    private final A2AServerRuntimeConfiguration configuration;
    private final DetectedTransports detectedTransports;

    // written by the async observers below, read from the threads serving the Agent Card
    private volatile HttpServerOptions httpServerOptions;
    private volatile HttpServerOptions httpsServerOptions;

    public ConfigCardBuilderCustomizer(A2AServerRuntimeConfiguration configuration,
            DetectedTransports detectedTransports) {
        this.configuration = configuration;
        this.detectedTransports = detectedTransports;
    }

    /**
     * @param tenant the tenant the agent is exposed under, or {@code null} for the agent served to requests that name
     *        none
     */
    public void customize(AgentCard.Builder cardBuilder, String tenant) {
        TenantConfiguration tenantConfiguration = tenant != null ? configuration.tenants().get(tenant) : null;
        populateSupportedInterfaces(cardBuilder, tenant, tenantConfiguration);
        cardBuilder
                .name(firstPresent(tenantConfiguration != null ? tenantConfiguration.name() : Optional.empty(),
                        configuration.name()))
                // the specification requires the field to be present, but tolerates it being empty
                .description(firstPresent(
                        tenantConfiguration != null ? tenantConfiguration.description() : Optional.empty(),
                        configuration.description().orElse("")))
                .version(firstPresent(tenantConfiguration != null ? tenantConfiguration.version() : Optional.empty(),
                        configuration.version()));
    }

    private static String firstPresent(Optional<String> tenantValue, String serverValue) {
        return tenantValue.isPresent() ? tenantValue.get() : serverValue;
    }

    /**
     * Each interface names the tenant too, which is how a client learns what to put in the {@code tenant} field of
     * the requests it sends to the agent.
     */
    private void populateSupportedInterfaces(AgentCard.Builder cardBuilder, String tenant,
            TenantConfiguration tenantConfiguration) {
        String url = determineUrl(tenantConfiguration);
        List<AgentInterface> interfaces = new ArrayList<>();
        for (String protocolBinding : detectedTransports.getProtocolBindings()) {
            interfaces.add(new AgentInterface(protocolBinding, url, tenant));
        }
        cardBuilder.supportedInterfaces(interfaces);
    }

    private String determineUrl(TenantConfiguration tenantConfiguration) {
        if (tenantConfiguration != null && tenantConfiguration.url().isPresent()) {
            return tenantConfiguration.url().get();
        }
        if (configuration.url().isPresent()) {
            return configuration.url().get();
        }
        // HTTPS takes precedence: a card served over it must not point clients at a plaintext endpoint
        HttpServerOptions secureOptions = httpsServerOptions;
        if (secureOptions != null) {
            return urlFromBoundAddress("https", secureOptions, HTTPS_PORT_PROPERTY);
        }
        HttpServerOptions plainOptions = httpServerOptions;
        if (plainOptions != null) {
            return urlFromBoundAddress("http", plainOptions, HTTP_PORT_PROPERTY);
        }
        // the SDK keeps the first card it is handed, so a guessed URL would be served for the life of the process,
        // while a refusal only fails the requests that arrive before the server has announced its address
        throw urlCannotBeDetermined("the HTTP server has not announced the address it is bound to yet");
    }

    private static String urlFromBoundAddress(String scheme, HttpServerOptions options, String portProperty) {
        return "%s://%s:%d".formatted(scheme, asUriHost(reachable(options.getHost())), boundPort(options, portProperty));
    }

    /**
     * The port the server answers on. {@link HttpServerOptions#getPort()} holds the configured port rather than the
     * bound one, and Quarkus stores {@code -1} there when the configuration asks for port {@code 0} and the
     * operating system picks one instead. The port that was actually bound is published under the same property
     * name by a config source that outranks every other, and that happens before {@code HttpServerStart} is fired,
     * so it is in place by the time an observer of that event reads it.
     */
    private static int boundPort(HttpServerOptions options, String portProperty) {
        int port = ConfigProvider.getConfig().getOptionalValue(portProperty, Integer.class).orElseGet(options::getPort);
        if (port <= 0) {
            throw urlCannotBeDetermined("the operating system chose the port the server is bound to "
                    + "and Quarkus has not published which one");
        }
        return port;
    }

    /**
     * A wildcard host is the address the server listens on, not one a client can dial, and the Agent Card exists to
     * be dialled. Quarkus binds to the wildcard address outside dev and test mode, so the card names the address
     * this host's name resolves to instead. Where that is a loopback address, as {@code /etc/hosts} makes it on some
     * Linux distributions, or where clients come through a proxy or an ingress, it is not one they can use, and
     * {@code quarkus.langchain4j.a2a.server.url} is what names the endpoint for them.
     */
    static String reachable(String host) {
        if (!WILDCARD_HOSTS.contains(host)) {
            return host;
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw urlCannotBeDetermined("the HTTP server is bound to the wildcard address '" + host
                    + "' and this host has no address of its own to advertise");
        }
    }

    /**
     * An IPv6 literal holds colons, which a URI would read as the start of the port unless the address is bracketed.
     */
    static String asUriHost(String host) {
        return host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static IllegalStateException urlCannotBeDetermined(String reason) {
        return new IllegalStateException("The URL of the A2A agent cannot be determined because " + reason
                + ". Set 'quarkus.langchain4j.a2a.server.url' to declare it explicitly");
    }

    void httpStarted(@ObservesAsync HttpServerStart start) {
        this.httpServerOptions = start.options();
    }

    void httpsStarted(@ObservesAsync HttpsServerStart start) {
        this.httpsServerOptions = start.options();
    }
}

package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.vertx.http.HttpServerStart;
import io.quarkus.vertx.http.HttpsServerStart;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerConfig;

/**
 * The address the HTTP server announced when it started, which is where the A2A routes are reached unless the
 * configuration names another.
 * <p>
 * This bean is kept when the application exposes no agent, so that the address is known by the time a dev mode live
 * reload brings the first one: the reload keeps the server running and never announces it again.
 */
@ApplicationScoped
public class ServerAddress {

    private static final Set<String> WILDCARD_HOSTS = Set.of("0.0.0.0", "::", "::0", "0:0:0:0:0:0:0:0");
    private static final String HTTP_PORT_PROPERTY = "quarkus.http.port";
    private static final String HTTPS_PORT_PROPERTY = "quarkus.http.ssl-port";

    /**
     * What the observers learned, kept across a dev mode live reload. A reload keeps the HTTP server and so never fires
     * the start events again, while it builds a new instance of this bean. The value belongs to the {@link Vertx} it
     * was learned for, which dev mode reuses across reloads and every other start replaces.
     */
    private static volatile BoundAddress boundAddress;

    private final Vertx vertx;

    public ServerAddress(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * The scheme, host and port of the server, preferring HTTPS when the server serves it.
     */
    public String url() {
        BoundAddress bound = boundAddress;
        if (bound != null && bound.vertx == vertx) {
            if (bound.https != null) {
                return bound.https.urlOrFail();
            }
            if (bound.http != null) {
                return bound.http.urlOrFail();
            }
        }
        throw urlCannotBeDetermined("the HTTP server has not announced the address it is bound to yet");
    }

    void httpStarted(@ObservesAsync HttpServerStart start) {
        remember(advertise("http", start.config(), HTTP_PORT_PROPERTY), false);
    }

    void httpsStarted(@ObservesAsync HttpsServerStart start) {
        remember(advertise("https", start.config(), HTTPS_PORT_PROPERTY), true);
    }

    private void remember(Advertised advertised, boolean secure) {
        synchronized (ServerAddress.class) {
            BoundAddress current = boundAddress;
            Advertised http = null;
            Advertised https = null;
            if (current != null && current.vertx == vertx) {
                http = current.http;
                https = current.https;
            }
            if (secure) {
                https = advertised;
            } else {
                http = advertised;
            }
            boundAddress = new BoundAddress(vertx, http, https);
        }
    }

    private static Advertised advertise(String scheme, HttpServerConfig config, String portProperty) {
        try {
            String url = "%s://%s:%d".formatted(scheme, asUriHost(reachable(config.getTcpHost())),
                    boundPort(config, portProperty));
            return new Advertised(url, null);
        } catch (IllegalStateException e) {
            return new Advertised(null, e.getMessage());
        }
    }

    /**
     * The port the server answers on. {@link HttpServerConfig#getTcpPort()} holds the configured port rather than the
     * bound one, and Quarkus stores {@code -1} there when the configuration asks for port {@code 0} and the
     * operating system picks one instead. The port that was actually bound is published under the same property
     * name by a config source that outranks every other, and that happens before {@code HttpServerStart} is fired,
     * so it is in place by the time an observer of that event reads it.
     */
    private static int boundPort(HttpServerConfig config, String portProperty) {
        Optional<Integer> published = ConfigProvider.getConfig().getOptionalValue(portProperty, Integer.class);
        int port = published.isPresent() ? published.get() : config.getTcpPort();
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

    /**
     * The URL the server is reached at, or why it could not be worked out.
     */
    private record Advertised(String url, String problem) {

        String urlOrFail() {
            if (url == null) {
                throw new IllegalStateException(problem);
            }
            return url;
        }
    }

    private record BoundAddress(Vertx vertx, Advertised http, Advertised https) {
    }
}

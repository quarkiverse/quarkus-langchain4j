package io.quarkiverse.langchain4j.testing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.microprofile.config.ConfigProvider;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

/**
 * This class is used instead of {@link io.quarkiverse.wiremock.devservice.ConnectWireMock} because the latter does not
 * work well with {@code QuarkusUnitTest}
 */
public abstract class WiremockAware {

    private WireMock wireMock;

    public static String wiremockUrlForConfig() {
        return "http://localhost:${quarkus.wiremock.devservices.port}";
    }

    public static String wiremockUrlForConfig(String path) {
        if (path.isEmpty()) {
            return wiremockUrlForConfig();
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return wiremockUrlForConfig() + path;

    }

    /**
     * This is meant to be called by test methods or pre- and post-test methods
     */
    public String resolvedWiremockUrl() {
        return String.format("http://localhost:%d", getResolvedWiremockPort());
    }

    /**
     * This is meant to be called by test methods or pre- and post-test methods
     */
    public String resolvedWiremockUrl(String path) {
        if (path.isEmpty()) {
            return resolvedWiremockUrl();
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return resolvedWiremockUrl() + path;
    }

    /**
     * This is meant to be called by test methods or pre- and post-test methods
     */
    protected WireMock wiremock() {
        if (wireMock == null) {
            wireMock = new WireMock(getResolvedWiremockPort());
        }
        return wireMock;
    }

    protected Integer getResolvedWiremockPort() {
        return ConfigProvider.getConfig().getValue("quarkus.wiremock.devservices.port", Integer.class);
    }

    protected void resetRequests() {
        wiremock().resetRequests();
    }

    protected void resetMappings() {
        wiremock().resetMappings();
    }

    protected LoggedRequest singleLoggedRequest() {
        assertThat(wiremock().getServeEvents()).hasSize(1);
        ServeEvent serveEvent = wiremock().getServeEvents().get(0);
        return serveEvent.getRequest();
    }

    protected byte[] requestBodyOfSingleRequest() {
        return singleLoggedRequest().getBody();
    }

    protected byte[] getRequestBody(ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        assertThat(request.getBody()).isNotEmpty();
        return request.getBody();
    }
}

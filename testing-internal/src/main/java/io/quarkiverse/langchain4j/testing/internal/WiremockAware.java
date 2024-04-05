package io.quarkiverse.langchain4j.testing.internal;

import org.eclipse.microprofile.config.ConfigProvider;

import com.github.tomakehurst.wiremock.client.WireMock;

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
    public WireMock wiremock() {
        if (wireMock == null) {
            wireMock = new WireMock(getResolvedWiremockPort());
        }
        return wireMock;
    }

    private Integer getResolvedWiremockPort() {
        return ConfigProvider.getConfig().getValue("quarkus.wiremock.devservices.port", Integer.class);
    }
}

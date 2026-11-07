package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * A stub Registry that answers a search only when the request carries the expected Authorization
 * header, and 401 otherwise, plus an OAuth2 token endpoint.
 * <p>
 * QuarkusUnitTest loads a test class twice, once to build the archive and once to run the test, so
 * statics are not shared. The port is handed over in a system property, and tests verify requests
 * through {@link #client(int)}, not the server object.
 */
final class RegistryStub {

    static final String TOKEN = "stub-access-token";
    static final String SEARCH_PATH = "/apis/registry/v3/search/artifacts";

    private RegistryStub() {
    }

    /**
     * Starts one stub per test class, expecting the given Authorization header value. Surefire loads
     * every test class before running any, so each class needs its own stub.
     */
    static int start(Class<?> testClass, String expectedAuthorization) {
        String property = property(testClass);
        String existing = System.getProperty(property);
        if (existing != null) {
            return Integer.parseInt(existing);
        }
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        // The test classes can't stop it themselves: @AfterAll runs in another class loader.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.stubFor(any(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(401)));
        server.stubFor(post(urlPathEqualTo("/token")).atPriority(1).willReturn(okJson(
                "{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        server.stubFor(get(urlPathEqualTo(SEARCH_PATH)).withHeader("Authorization", equalTo(expectedAuthorization))
                .atPriority(1).willReturn(okJson("{\"count\":0,\"artifacts\":[]}")));
        System.setProperty(property, String.valueOf(server.port()));
        return server.port();
    }

    static String registryUrl(int port) {
        return "http://localhost:" + port + "/apis/registry/v3";
    }

    static String tokenEndpoint(int port) {
        return "http://localhost:" + port + "/token";
    }

    static WireMock client(int port) {
        return new WireMock("localhost", port);
    }

    private static String property(Class<?> testClass) {
        return "test.apicurio.registry.stub.port." + testClass.getSimpleName();
    }

}

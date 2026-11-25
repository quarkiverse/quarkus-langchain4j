package io.quarkiverse.langchain4j.agentic.devmode;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * Verifies that a {@code @ConfigProperty} parameter of a static helper method is resolved from the configuration in
 * dev mode, as in a normal build.
 */
public class ConfigPropertyHelperParameterDevModeTest {

    static final int HTTP_PORT = findAvailablePort();

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(PricingResource.class, PricingWorkflow.class, PricingWorkflow.PricingAgent.class)
                            .addAsResource(new StringAsset(String.join("\n",
                                    "quarkus.http.port=" + HTTP_PORT,
                                    "pricing-agent.url=http://localhost:1/pricing")),
                                    "application.properties"));

    @Test
    void configPropertyOfAnA2AServerUrlSupplier() {
        assertThat(get("/pricing"))
                .contains("Failed to fetch the agent card of A2A agent 'price' from http://localhost:1/pricing");
    }

    private static String get(String path) {
        return given()
                .port(HTTP_PORT)
                .get(path)
                .then()
                .statusCode(200)
                .extract()
                .asString();
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to find an available HTTP port for dev mode test", e);
        }
    }
}

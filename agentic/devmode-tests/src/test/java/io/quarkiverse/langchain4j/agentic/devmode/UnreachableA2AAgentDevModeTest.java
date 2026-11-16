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
 * Verifies that the Topology page of the Dev UI shows an agentic system with a remote A2A agent that is not
 * reachable: the agent card is not needed to create the system, and the description that cannot be read from it is
 * reported as unavailable for that agent only.
 */
public class UnreachableA2AAgentDevModeTest {

    static final int HTTP_PORT = findAvailablePort();

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(RemoteStoryResource.class, RemoteStoryWorkflow.class,
                                    RemoteStoryWorkflow.RemoteWriter.class, RemoteStoryWorkflow.Publisher.class)
                            .addAsResource(new StringAsset("quarkus.http.port=" + HTTP_PORT), "application.properties"));

    @Test
    void unreachableA2AAgentInTopologyPage() {
        // the page renders the report of the upstream HtmlReportGenerator
        assertThat(get("/remote-story/topology"))
                .contains("writeStory")
                .contains("unavailable: Failed to fetch the agent card of A2A agent")
                .contains("publish")
                .contains("Publishes a story");
    }

    private static String get(String path) {
        return given()
                .port(HTTP_PORT)
                .get(path)
                .then()
                .statusCode(200)
                .extract().asString();
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

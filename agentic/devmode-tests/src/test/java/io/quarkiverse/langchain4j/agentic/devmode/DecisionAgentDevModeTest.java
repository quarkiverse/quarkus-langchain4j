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
 * Verifies that the Topology and Executions pages of the Dev UI show a decision agent, its kind and the token usage
 * of its decision model.
 */
public class DecisionAgentDevModeTest {

    static final int HTTP_PORT = findAvailablePort();

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(SpamResource.class, SpamWorkflow.class, SpamWorkflow.SpamGate.class,
                                    SpamWorkflow.Responder.class)
                            .addAsResource(new StringAsset(String.join("\n",
                                    "quarkus.http.port=" + HTTP_PORT,
                                    "quarkus.langchain4j.openai.decision-model.enabled=false")),
                                    "application.properties"));

    @Test
    void decisionAgentInDevUiPages() {
        assertThat(get("/spam")).isEqualTo("rejected");

        // the pages render the reports of the upstream HtmlReportGenerator
        assertThat(get("/spam/topology"))
                .contains("spamGate")
                .contains("topology-badge decision");
        assertThat(get("/spam/executions"))
                .contains("spamGate")
                .contains("fixed-model")
                .contains("isSpam = YesNoAnswer{probability=0.9}");
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

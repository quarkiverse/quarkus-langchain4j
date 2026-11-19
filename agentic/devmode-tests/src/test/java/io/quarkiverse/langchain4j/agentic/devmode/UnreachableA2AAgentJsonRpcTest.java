package io.quarkiverse.langchain4j.agentic.devmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.function.Supplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;
import tools.jackson.databind.JsonNode;

/**
 * Verifies that the topology JSON of an agentic system with an unreachable remote A2A agent reports the description
 * of that agent as unavailable, like the LangChain4j report, instead of failing as a whole.
 */
public class UnreachableA2AAgentJsonRpcTest extends DevUIJsonRPCTest {

    static final int HTTP_PORT = findAvailablePort();

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(new TestApplication());

    public UnreachableA2AAgentJsonRpcTest() {
        super("quarkus-langchain4j-agentic");
    }

    @Test
    void topologyReportsTheUnreachableAgentAsUnavailable() throws Exception {
        JsonNode topology = executeJsonRPCMethod("getTopology", Map.of("index", 0));

        assertThat(topology.get("error")).isNull();
        JsonNode writer = subagent(topology.get("root"), "writeStory");
        assertThat(writer.get("description").asText())
                .startsWith("unavailable: Failed to fetch the agent card of A2A agent");
        assertThat(subagent(topology.get("root"), "publish").get("description").asText())
                .isEqualTo("Publishes a story");
    }

    private static JsonNode subagent(JsonNode node, String name) {
        for (JsonNode child : node.get("subagents")) {
            if (name.equals(child.get("name").asText())) {
                return child;
            }
        }
        throw new AssertionError("No sub-agent " + name + " in " + node);
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class TestApplication implements Supplier<JavaArchive> {

        @Override
        public JavaArchive get() {
            return ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RemoteStoryResource.class, RemoteStoryWorkflow.class,
                            RemoteStoryWorkflow.RemoteWriter.class, RemoteStoryWorkflow.Publisher.class)
                    .addAsResource(new StringAsset("quarkus.http.port=" + HTTP_PORT), "application.properties");
        }
    }
}

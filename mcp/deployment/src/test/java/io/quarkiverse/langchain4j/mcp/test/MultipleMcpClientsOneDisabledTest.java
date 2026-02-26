package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIterable;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleMcpClientsOneDisabledTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMockHttpMcpServer.class, MockHttpMcpServer.class, Mock2HttpMcpServer.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mock-mcp/sse
                            quarkus.langchain4j.mcp.client2.enabled=false
                            quarkus.langchain4j.mcp.client2.transport-type=http
                            quarkus.langchain4j.mcp.client2.url=http://localhost:8081/mock2-mcp/sse
                            """),
                            "application.properties"));

    @Inject
    @McpClientName("client1")
    Instance<McpClient> client1Instance;

    @Inject
    @McpClientName("client2")
    Instance<McpClient> client2Instance;

    @Inject
    ToolProvider toolProvider;

    @Test
    public void oneClientDisabled() {
        assertThat(client1Instance.isResolvable()).isTrue();
        assertThat(client2Instance.isResolvable()).isFalse();

        ToolProviderResult result = toolProvider.provideTools(null);
        Set<String> toolNames = result.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        assertThatIterable(toolNames).containsExactlyInAnyOrder("add", "longRunningOperation", "logging");
    }
}

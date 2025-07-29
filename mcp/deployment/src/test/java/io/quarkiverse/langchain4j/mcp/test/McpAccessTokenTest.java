package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.QuarkusMcpToolProvider;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Test MCP clients over an HTTP transport.
 * This is a very rudimentary test that runs against a mock MCP server. The plan is
 * to replace it with a more proper MCP server once we have an appropriate Java SDK ready for it.
 */
public class McpAccessTokenTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMockHttpMcpServer.class, MockHttpMcpAccessTokenServer.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.mcp.client1.transport-type=http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mock-access-token-mcp/sse
                            """),
                            "application.properties"));

    @Inject
    ToolProvider toolProvider;

    @Test
    public void toolProviderShouldBeMcpBased() {
        assertThat(ClientProxy.unwrap(toolProvider)).isInstanceOf(QuarkusMcpToolProvider.class);
    }

    @Test
    public void providingTools() {
        // obtain a list of tools from the MCP server
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        // verify the list of tools
        assertThat(toolProviderResult.tools().size()).isEqualTo(3);
        assertThat(toolProviderResult.tools().keySet().stream().map(ToolSpecification::name).toList())
                .containsExactlyInAnyOrder("add", "get_resource", "list_resources");
        assertThat(toolProviderResult.tools().keySet().stream().map(ToolSpecification::description).toList())
                .containsExactlyInAnyOrder("Adds two numbers", "Lists all available resources.",
                        "Retrieves a resource identified by the MCP server name and the resource URI.The 'list_resources' tool should be called before this one to obtain the list.");

        var addTool = toolProviderResult.tools().keySet().stream().filter(spec -> "add".equals(spec.name())).findFirst();
        assertThat(addTool)
                .get()
                .extracting(
                        spec -> spec.parameters().properties().get("a").description(),
                        spec -> spec.parameters().properties().get("b").description())
                .containsExactly("First number", "Second number");
    }

    @ApplicationScoped
    public static class TestMcpAuthProvider implements McpClientAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            try {
                Thread.sleep(5000);
                return "Bearer test-token";
            } catch (Exception ex) {
            }
            return null;
        }

    }
}

package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.mcp.test.mock.ModernMcpMockServer;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that an agent declaring {@link McpToolBox} receives the tools of the MCP server.
 */
public class AgentMcpClientTest extends WiremockAware {

    private static final String MCP_PATH = "/mcp/agent";

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Sequence.class, AgentWithMcpTools.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.protocol-version=%s
                            quarkus.langchain4j.mcp.client1.url=%s
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            quarkus.langchain4j.mcp.client1.tool-execution-timeout=1s
                            """.formatted(ModernMcpMockServer.PROTOCOL_VERSION, wiremockUrlForConfig(MCP_PATH))),
                            "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever");

    @Inject
    Sequence sequence;

    @Inject
    MockModelServer mock;

    public interface Sequence {

        @SequenceAgent(outputKey = "toolsList", subAgents = { AgentWithMcpTools.class })
        String toolsList(@V("userMessage") String userMessage);
    }

    public interface AgentWithMcpTools {

        @Agent(outputKey = "toolsList")
        @McpToolBox
        String toolsList(@V("userMessage") String userMessage);
    }

    @BeforeEach
    void setUpMcpServer() {
        mock.reset();
        McpMockServer.modern(wiremock(), MCP_PATH)
                .reset()
                .stubInitialization()
                .stubTools(McpTool.ADD);
    }

    @Test
    @ActivateRequestContext
    public void agentHasTools() {
        mock.chat().reply("dummy");

        var response = sequence.toolsList("test");
        assertEquals("dummy", response);
        assertThat(ToolsInterceptor.TOOLS_NAMES).containsExactly("add");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
        assertThat(request.asChat().tools()).extracting(ChatRequestView.ToolDefinition::name).containsExactly("add");
    }

    @Singleton
    public static class ToolsInterceptor implements ChatModelListener {

        public static final CopyOnWriteArrayList<String> TOOLS_NAMES = new CopyOnWriteArrayList<>();

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            requestContext.chatRequest().toolSpecifications().forEach(ts -> TOOLS_NAMES.add(ts.name()));
        }
    }
}

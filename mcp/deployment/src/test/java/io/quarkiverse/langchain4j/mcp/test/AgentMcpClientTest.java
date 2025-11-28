package io.quarkiverse.langchain4j.mcp.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Test MCP clients over an HTTP transport.
 * This is a very rudimentary test that runs against a mock MCP server. The plan is
 * to replace it with a more proper MCP server once we have an appropriate Java SDK ready for it.
 */
public class AgentMcpClientTest extends OpenAiBaseTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMockHttpMcpServer.class, MockHttpMcpServer.class, Sequence.class,
                            AgentWithMcpTools.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mock-mcp/sse
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            quarkus.langchain4j.mcp.client1.tool-execution-timeout=1s
                            """),
                            "application.properties"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    Sequence sequence;

    public interface Sequence {

        @SequenceAgent(outputKey = "toolsList", subAgents = { AgentWithMcpTools.class })
        String toolsList(@V("userMessage") String userMessage);
    }

    public interface AgentWithMcpTools {

        @Agent(outputKey = "toolsList")
        @McpToolBox
        String toolsList(@V("userMessage") String userMessage);
    }

    @Test
    @ActivateRequestContext
    public void agentHasTools() {
        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("""
                                                {
                                                        "id": "chatcmpl-8GRu6o9Qf9JFAebDqpj76H5fl6Naz",
                                                        "object": "chat.completion",
                                                        "created": 1698931202,
                                                        "model": "gpt-3.5-turbo-0613",
                                                        "choices": [
                                                          {
                                                            "index": 0,
                                                            "message": {
                                                              "role": "assistant",
                                                              "content": "dummy"
                                                            },
                                                            "finish_reason": "stop"
                                                          }
                                                        ],
                                                        "usage": {
                                                          "prompt_tokens": 159,
                                                          "completion_tokens": 13,
                                                          "total_tokens": 172
                                                        }
                                                      }
                                                  \s""")));

        var response = sequence.toolsList("test");
        assertEquals("dummy", response);
        assertThat(ToolsInterceptor.TOOLS_NAMES).hasSize(3);
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

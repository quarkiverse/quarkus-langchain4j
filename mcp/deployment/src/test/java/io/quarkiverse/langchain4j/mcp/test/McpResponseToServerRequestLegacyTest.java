package io.quarkiverse.langchain4j.mcp.test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.test.mock.LegacyMcpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The client's response to a server-initiated request expects no reply, so it must not be registered
 * as a pending operation. Its ID comes from the server's ID space, so registering it would displace
 * a client request that is still in flight and happens to carry the same ID, leaving that request
 * to time out.
 * <p>
 * The server sends a {@code ping} with the same ID as the tool call, on the tool call's SSE stream,
 * before the result of the tool call.
 * <p>
 * There is no counterpart for the modern protocol, because servers can't send requests to the client there.
 */
public class McpResponseToServerRequestLegacyTest extends WiremockAware {

    private static final String MCP_PATH = "/mcp/response-to-server-request-legacy";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.protocol-version=%s
                            quarkus.langchain4j.mcp.client1.url=%s
                            quarkus.langchain4j.mcp.client1.log-requests=true
                            quarkus.langchain4j.mcp.client1.log-responses=true
                            quarkus.langchain4j.mcp.client1.tool-execution-timeout=5s
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            """.formatted(LegacyMcpMockServer.PROTOCOL_VERSION, wiremockUrlForConfig(MCP_PATH))),
                            "application.properties"));

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @BeforeEach
    void setUpMcpServer() {
        McpMockServer.legacy(wiremock(), MCP_PATH)
                .reset()
                .stubInitialization()
                .stubClientResponses()
                .stubTools(McpTool.ADD)
                .stubToolCallWithServerPing("add", "The sum of 5 and 12 is 17.");
    }

    @Test
    public void responseToServerPingDoesNotDisplaceThePendingToolCall() throws Exception {
        mcpClient.listTools();
        String result = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("add")
                .arguments("{\"a\": 5, \"b\": 12}")
                .build())
                .resultText();
        assertThat(result).isEqualTo("The sum of 5 and 12 is 17.");

        // the ID of the ping stays on the wire, so the server can still correlate the response
        // (the response is sent asynchronously, so it may arrive after the tool call completes)
        ObjectMapper mapper = new ObjectMapper();
        JsonNode toolCall = mapper.readTree(singleRequest(postRequestedFor(urlPathEqualTo(MCP_PATH))
                .withRequestBody(matchingJsonPath("$.method", equalTo("tools/call")))).getBodyAsString());
        RequestPatternBuilder pingResponsePattern = postRequestedFor(urlPathEqualTo(MCP_PATH))
                .withRequestBody(matchingJsonPath("$.result", matching(".*")));
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !wiremock().find(pingResponsePattern).isEmpty());
        JsonNode pingResponse = mapper.readTree(singleRequest(pingResponsePattern).getBodyAsString());
        assertThat(pingResponse.get("id").asLong()).isEqualTo(toolCall.get("id").asLong());
    }

    private LoggedRequest singleRequest(RequestPatternBuilder pattern) {
        List<LoggedRequest> requests = wiremock().find(pattern);
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }
}

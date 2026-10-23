package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;

/**
 * Tests the MCP client spans in the packaged application (in native mode, the native executable), with the
 * application in {@code src/main}.
 */
@QuarkusIntegrationTest
public class McpTracingNativeIT {

    private static final int HTTP_PORT = 8283;

    private static Process httpServer;

    @BeforeAll
    static void startHttpServer() throws Exception {
        skipTestsIfJbangNotAvailable();
        httpServer = startServerHttp("tracing_mcp_server.java", HTTP_PORT);
    }

    @AfterAll
    static void stopHttpServer() {
        if (httpServer != null && httpServer.isAlive()) {
            McpServerHelper.destroyProcessTree(httpServer);
        }
    }

    @Test
    public void toolCallOverStdio() {
        Map<String, Object> mcpSpan = verifyToolCallSpan("stdio");
        // no HTTP requests are sent over stdio
        assertThat(spans()).noneMatch(span -> "POST".equals(span.get("name"))
                && mcpSpan.get("spanId").equals(span.get("parentSpanId")));
    }

    @Test
    public void toolCallOverStreamableHttp() {
        Map<String, Object> mcpSpan = verifyToolCallSpan("http");
        // the span of the HTTP request that carries the tool call is a child of the MCP span
        awaitSpan(span -> span.get("kind").equals("CLIENT") && span.get("name").equals("POST")
                && span.get("parentSpanId").equals(mcpSpan.get("spanId"))
                && span.get("traceId").equals(mcpSpan.get("traceId")));
    }

    /**
     * Calls a tool and verifies its span: its context is propagated to the server and it's a child of the span of
     * the REST request that called the tool.
     */
    private Map<String, Object> verifyToolCallSpan(String client) {
        // 00-<trace ID>-<span ID>-<flags>
        String traceparent = RestAssured.get("/tracing/call/" + client).then().statusCode(200).extract().asString();
        String[] parts = traceparent.split("-");
        assertThat(parts).as("traceparent received by the server: " + traceparent).hasSize(4);
        String traceId = parts[1];
        String spanId = parts[2];

        Map<String, Object> mcpSpan = awaitSpan(span -> span.get("spanId").equals(spanId));
        assertThat(mcpSpan.get("name")).isEqualTo("tools/call echoMeta");
        assertThat(mcpSpan.get("kind")).isEqualTo("CLIENT");
        assertThat(mcpSpan.get("mcpMethodName")).isEqualTo("tools/call");
        assertThat(mcpSpan.get("traceId")).isEqualTo(traceId);

        Map<String, Object> restSpan = awaitSpan(span -> span.get("spanId").equals(mcpSpan.get("parentSpanId")));
        assertThat(restSpan.get("kind")).isEqualTo("SERVER");
        assertThat((String) restSpan.get("name")).contains("/tracing/call/");
        assertThat(restSpan.get("traceId")).isEqualTo(traceId);
        return mcpSpan;
    }

    private static Map<String, Object> awaitSpan(Predicate<Map<String, Object>> predicate) {
        return await().atMost(Duration.ofSeconds(30))
                .until(() -> spans().stream().filter(predicate).findFirst(), Optional::isPresent)
                .orElseThrow();
    }

    private static List<Map<String, Object>> spans() {
        return RestAssured.get("/tracing/spans").then().statusCode(200).extract().jsonPath().getList("$");
    }
}

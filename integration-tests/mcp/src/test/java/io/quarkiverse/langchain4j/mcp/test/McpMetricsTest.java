package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

public class McpMetricsTest {

    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url",
                    "http://localhost:8082/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.metrics.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @Inject
    MeterRegistry meterRegistry;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("metrics_mcp_server.java");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @AfterEach
    public void clear() {
        meterRegistry.clear();
    }

    @Test
    public void testToolCallSuccess() {
        mcpClient.executeTool(ToolExecutionRequest.builder().name("foo").build());
        Timer timer = meterRegistry
                .get("mcp.client.tool.call.duration")
                .tag("mcp_client", "client1")
                .tag("outcome", "success")
                .tag("tool_name", "foo")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    @Test
    public void testToolCallBusinessError() {
        try {
            mcpClient.executeTool(ToolExecutionRequest.builder().name("businessError").build());
            fail("Expected ToolExecutionException");
        } catch (Exception e) {
            Timer timer = meterRegistry
                    .get("mcp.client.tool.call.duration")
                    .tag("mcp_client", "client1")
                    .tag("outcome", "failure")
                    .tag("tool_name", "businessError")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        }
    }

    @Test
    public void testToolCallProtocolError() {
        try {
            mcpClient.executeTool(ToolExecutionRequest.builder().name("protocolError").build());
            fail("Expected an exception");
        } catch (Exception e) {
            Timer timer = meterRegistry
                    .get("mcp.client.tool.call.duration")
                    .tag("mcp_client", "client1")
                    .tag("outcome", "error")
                    .tag("tool_name", "protocolError")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        }
    }

    @Test
    public void testResourceGetSuccess() {
        mcpClient.readResource("file:///text-ok");
        Timer timer = meterRegistry
                .get("mcp.client.resource.get.duration")
                .tag("mcp_client", "client1")
                .tag("outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    @Test
    public void testResourceGetError() {
        try {
            mcpClient.readResource("file:///text-fail");
            fail("Expected an exception");
        } catch (Exception e) {
            Timer timer = meterRegistry
                    .get("mcp.client.resource.get.duration")
                    .tag("mcp_client", "client1")
                    .tag("outcome", "error")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        }
    }

    @Test
    public void testPromptGetSuccess() {
        mcpClient.getPrompt("prompt", null);
        Timer timer = meterRegistry
                .get("mcp.client.prompt.get.duration")
                .tag("mcp_client", "client1")
                .tag("outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    @Test
    public void testPromptGetError() {
        try {
            mcpClient.getPrompt("promptFailing", null);
            fail("Expected an exception");
        } catch (Exception e) {
            Timer timer = meterRegistry
                    .get("mcp.client.prompt.get.duration")
                    .tag("mcp_client", "client1")
                    .tag("outcome", "error")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        }
    }

}

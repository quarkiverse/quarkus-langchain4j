package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.mcp.McpToolResolutionException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.V;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * An agentic system with an MCP client agent is created although its MCP server is down: the
 * {@code @McpClientSupplier} method is only invoked, and the client only connects, when the agent is first invoked.
 */
public class McpClientAgentUnreachableServerTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest();

    private static final AtomicInteger CREATED_CLIENTS = new AtomicInteger();

    public interface McpWeatherAgent {

        @McpClientAgent(toolName = "getWeatherForecast", outputKey = "weather", description = "Fetches weather forecast from MCP server")
        String fetchWeather(@V("destination") String destination);

        @McpClientSupplier
        static McpClient mcpClient() {
            CREATED_CLIENTS.incrementAndGet();
            // there isn't any MCP server
            return new DefaultMcpClient.Builder()
                    .transport(StreamableHttpMcpTransport.builder().url("http://localhost:1/mcp").build())
                    .build();
        }
    }

    public interface WeatherWorkflow {

        @SequenceAgent(outputKey = "weather", subAgents = McpWeatherAgent.class)
        String forecast(@V("destination") String destination);
    }

    @Inject
    WeatherWorkflow workflow;

    @Test
    void mcpClientIsCreatedWhenTheAgentIsFirstInvoked() {
        // creating the agentic system needs neither the MCP client nor its server
        ClientProxy.unwrap(workflow);
        assertThat(CREATED_CLIENTS).hasValue(0);

        assertThatThrownBy(new ThrowingCallable() {
            @Override
            public void call() {
                workflow.forecast("Rome");
            }
        }).isInstanceOf(AgentInvocationException.class)
                .hasStackTraceContaining(McpToolResolutionException.class.getName()
                        + ": Failed to resolve the MCP tool 'getWeatherForecast' of the MCP client agent 'fetchWeather'");
        assertThat(CREATED_CLIENTS).hasValue(1);
    }
}

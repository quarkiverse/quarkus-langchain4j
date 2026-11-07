package io.quarkiverse.langchain4j.agentic.deployment.validation;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class McpClientAgentShouldWorkTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Test
    void test() {
        // build succeeded without validation errors — that's the assertion
    }

    public interface McpWeatherAgent {

        @McpClientAgent(toolName = "getWeatherForecast", outputKey = "weather", description = "Fetches weather forecast from MCP server")
        String fetchWeather(String destination);

        @McpClientSupplier
        static Object mcpClient() {
            return null;
        }
    }

    public interface WorkflowWithMcpAgent {

        @SequenceAgent(outputKey = "result", subAgents = { DestinationAgent.class, McpWeatherAgent.class })
        String execute();
    }

    public interface DestinationAgent {

        @SystemMessage("You are a helpful assistant.")
        @Agent(description = "Provides a destination", outputKey = "destination")
        String ask();
    }
}

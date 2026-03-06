package io.quarkiverse.langchain4j.testing.mcpcompliance;

import jakarta.inject.Inject;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.testing.mcpcompliance.scenarios.Scenario;
import io.quarkiverse.langchain4j.testing.mcpcompliance.scenarios.ScenarioInitialize;
import io.quarkiverse.langchain4j.testing.mcpcompliance.scenarios.ScenarioToolsCall;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class McpTestingClientApplication implements QuarkusApplication {

    @Inject
    @McpClientName("conformance")
    McpClient client;

    @Override
    public int run(String... args) throws Exception {
        String scenarioString = System.getenv("MCP_CONFORMANCE_SCENARIO");
        if (scenarioString == null) {
            System.err.println("MCP_CONFORMANCE_SCENARIO environment variable is not set");
            return 1;
        }
        Scenario scenario;
        switch (scenarioString) {
            case "initialize" -> scenario = new ScenarioInitialize();
            case "tools_call" -> scenario = new ScenarioToolsCall();
            default -> {
                System.err.println("Unknown scenario: " + scenarioString);
                return 1;
            }
        }
        scenario.run(client);
        return 0;
    }
}

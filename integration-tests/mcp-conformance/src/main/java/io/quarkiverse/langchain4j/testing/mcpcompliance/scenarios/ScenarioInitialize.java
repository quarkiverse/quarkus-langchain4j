package io.quarkiverse.langchain4j.testing.mcpcompliance.scenarios;

import dev.langchain4j.mcp.client.McpClient;

public class ScenarioInitialize implements Scenario {

    @Override
    public void run(McpClient mcpClient) {
        // not strictly necessary to check health, but the scenario expects that the initialization will
        // complete successfully, so we need to do something to trigger the initialization
        mcpClient.checkHealth();
    }

}

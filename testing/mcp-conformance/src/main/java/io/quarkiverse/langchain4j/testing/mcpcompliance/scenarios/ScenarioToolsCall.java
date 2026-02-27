package io.quarkiverse.langchain4j.testing.mcpcompliance.scenarios;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;

public class ScenarioToolsCall implements Scenario {

    @Override
    public void run(McpClient mcpClient) {
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("add_numbers")
                .arguments("{\"a\": 1, \"b\": 2}")
                .build());
    }

}

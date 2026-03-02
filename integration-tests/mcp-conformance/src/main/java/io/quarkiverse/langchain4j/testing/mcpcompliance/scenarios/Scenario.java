package io.quarkiverse.langchain4j.testing.mcpcompliance.scenarios;

import dev.langchain4j.mcp.client.McpClient;

public interface Scenario {

    void run(McpClient mcpClient);

}

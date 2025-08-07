package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpRoot;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

public abstract class McpRootsTestBase {

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    private static final Logger log = LoggerFactory.getLogger(McpRootsTestBase.class);

    @Test
    public void verifyServerHasReceivedTools() {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("assertRoots")
                .arguments("{}")
                .build();
        String result = mcpClient.executeTool(toolExecutionRequest);
        assertThat(result).isEqualTo("OK");

        // now update the roots
        List<McpRoot> newRoots = new ArrayList<>();
        newRoots.add(new McpRoot("Paul's workspace", "file:///home/paul/workspace"));
        mcpClient.setRoots(newRoots);

        // and verify that the server has asked for the roots again and received them
        toolExecutionRequest = ToolExecutionRequest.builder()
                .name("assertRootsAfterUpdate")
                .arguments("{}")
                .build();
        result = mcpClient.executeTool(toolExecutionRequest);
        assertThat(result).isEqualTo("OK");
    }
}

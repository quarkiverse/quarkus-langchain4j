package io.quarkiverse.langchain4j.oidc.client.mcp.test;

import jakarta.ws.rs.Path;

@Path("/mcp2")
public class MockProvider2McpServer extends AbstractMockHttpMcpServer {

    private static final String TOOLS_LIST_RESPONSE = """
            {
              "result": {
                "tools": [
                  {
                    "name": "tool-from-server2",
                    "description": "A tool from server 2",
                    "inputSchema": {"type":"object","properties":{},"additionalProperties":false}
                  }
                ]
              },
              "jsonrpc": "2.0",
              "id": "%s"
            }
            """;

    @Override
    protected String getEndpoint() {
        return "mcp2";
    }

    @Override
    protected String getToolsListResponse() {
        return TOOLS_LIST_RESPONSE;
    }

    @Override
    protected boolean verifyAuthorization(String authorization) {
        return "Bearer token-from-provider2".equals(authorization);
    }
}

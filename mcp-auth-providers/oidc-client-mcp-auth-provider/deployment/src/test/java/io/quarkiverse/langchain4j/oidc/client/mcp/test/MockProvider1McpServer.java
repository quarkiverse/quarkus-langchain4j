package io.quarkiverse.langchain4j.oidc.client.mcp.test;

import jakarta.ws.rs.Path;

@Path("/mcp1")
public class MockProvider1McpServer extends AbstractMockHttpMcpServer {

    private static final String TOOLS_LIST_RESPONSE = """
            {
              "result": {
                "tools": [
                  {
                    "name": "tool-from-server1",
                    "description": "A tool from server 1",
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
        return "mcp1";
    }

    @Override
    protected String getToolsListResponse() {
        return TOOLS_LIST_RESPONSE;
    }

    @Override
    protected boolean verifyAuthorization(String authorization) {
        return "Bearer token-from-provider1".equals(authorization);
    }
}

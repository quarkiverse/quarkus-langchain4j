package io.quarkiverse.langchain4j.mcp.test;

import jakarta.ws.rs.Path;

/**
 * A very basic mock MCP server using the HTTP transport.
 */
@Path("/mock-access-token-mcp")
public class MockHttpMcpAccessTokenServer extends AbstractMockHttpMcpServer {

    // language=JSON
    public static final String TOOLS_LIST_RESPONSE = """
            {
              "result": {
                "tools": [
                  {
                    "name": "add",
                    "description": "Adds two numbers",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                        "a": {
                          "type": "number",
                          "description": "First number"
                        },
                        "b": {
                          "type": "number",
                          "description": "Second number"
                        }
                      },
                      "required": [
                        "a",
                        "b"
                      ],
                      "additionalProperties": false,
                      "$schema": "http://json-schema.org/draft-07/schema#"
                    }
                  }
                ]
              },
              "jsonrpc": "2.0",
              "id": "%s"
            }
            """;

    protected String getToolsListResponse() {
        return TOOLS_LIST_RESPONSE;
    }

    @Override
    protected String getEndpoint() {
        return "mock-access-token-mcp";
    }

    @Override
    protected boolean verifyAuthorization(String authorization) {
        return "Bearer test-token".equals(authorization);
    }
}

package io.quarkiverse.langchain4j.mcp.test;

import jakarta.ws.rs.Path;

/**
 * A very basic mock MCP server using the HTTP transport.
 */
@Path("/mock3-mcp")
public class Mock3HttpMcpServer extends AbstractMockHttpMcpServer {

    // language=JSON
    public static final String TOOLS_LIST_RESPONSE = """
            {
              "result": {
                "tools": [
                  {
                    "name": "multiply",
                    "description": "Multiplies two numbers",
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
        return "mock3-mcp";
    }
}

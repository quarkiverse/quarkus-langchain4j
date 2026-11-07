package io.quarkiverse.langchain4j.mcp.test;

import jakarta.ws.rs.Path;

/**
 * A very basic mock MCP server using the HTTP transport.
 */
@Path("/mock-mcp")
public class MockHttpMcpServer extends AbstractMockHttpMcpServer {

    // language=JSON
    public static final String TOOLS_LIST_RESPONSE = """
            {
              "result": {
                "tools": [
                  {
                    "name": "longRunningOperation",
                    "description": "Demonstrates a long running operation with progress updates",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                        "duration": {
                          "type": "number",
                          "default": 10,
                          "description": "Duration of the operation in seconds"
                        },
                        "steps": {
                          "type": "number",
                          "default": 5,
                          "description": "Number of steps in the operation"
                        }
                      },
                      "additionalProperties": false,
                      "$schema": "http://json-schema.org/draft-07/schema#"
                    }
                  },
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
                  },
                  {
                    "name": "logging",
                    "description": "Sends a log message to the client and then just returns 'OK'",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                      },
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

    @Override
    protected String getToolsListResponse() {
        return TOOLS_LIST_RESPONSE;
    }

    @Override
    protected String getEndpoint() {
        return "mock-mcp";
    }
}

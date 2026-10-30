package io.quarkiverse.langchain4j.mcp.test.mock;

/**
 * A tool definition that a mock MCP server can advertise in its {@code tools/list} response.
 *
 * @param name the name of the tool
 * @param definition the JSON definition of the tool, as it appears in the {@code tools} array
 */
public record McpTool(String name, String definition) {

    // language=JSON
    public static final McpTool ADD = new McpTool("add", """
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
                "required": ["a", "b"],
                "additionalProperties": false,
                "$schema": "http://json-schema.org/draft-07/schema#"
              }
            }
            """);

    // language=JSON
    public static final McpTool SUBTRACT = new McpTool("subtract", """
            {
              "name": "subtract",
              "description": "Subtracts two numbers",
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
                "required": ["a", "b"],
                "additionalProperties": false,
                "$schema": "http://json-schema.org/draft-07/schema#"
              }
            }
            """);

    // language=JSON
    public static final McpTool MULTIPLY = new McpTool("multiply", """
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
                "required": ["a", "b"],
                "additionalProperties": false,
                "$schema": "http://json-schema.org/draft-07/schema#"
              }
            }
            """);

    // language=JSON
    public static final McpTool LONG_RUNNING_OPERATION = new McpTool("longRunningOperation", """
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
            }
            """);

    // language=JSON
    public static final McpTool LOGGING = new McpTool("logging", """
            {
              "name": "logging",
              "description": "Sends a log message to the client and then just returns 'OK'",
              "inputSchema": {
                "type": "object",
                "properties": {},
                "additionalProperties": false,
                "$schema": "http://json-schema.org/draft-07/schema#"
              }
            }
            """);
}

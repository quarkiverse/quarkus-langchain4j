# Apicurio Registry MCP Server Discovery Sample

This sample demonstrates how to use the Quarkus LangChain4j Apicurio Registry integration
to dynamically discover and connect to MCP servers registered in an Apicurio Registry instance.

## Prerequisites

- Docker (for running Apicurio Registry)
- JBang (for running the sample MCP server)
- Ollama running locally with a model that supports tool calling (e.g. `qwen2.5:7b`)

## Running

### 1. Start Apicurio Registry

```bash
docker run -p 8180:8080 quay.io/apicurio/apicurio-registry:3.3.0
```

### 2. Start the sample MCP server

The included `mcp_server.java` JBang script provides simple math tools (add, multiply) and a current-time tool:

```bash
jbang -Dquarkus.http.port=8085 mcp_server.java
```

### 3. Register the MCP server in the registry

Register the MCP tool definition as an `MCP_TOOL` artifact with connection metadata in labels:

```bash
curl -X POST http://localhost:8180/apis/registry/v3/groups/default/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactId": "calculator-server",
    "artifactType": "MCP_TOOL",
    "name": "Calculator Server",
    "description": "A simple calculator MCP server with add, multiply, and current-time tools",
    "labels": {
      "mcp-server-url": "http://localhost:8085/mcp",
      "mcp-transport-type": "streamable-http"
    },
    "firstVersion": {
      "content": {
        "content": "{\"name\":\"add\",\"description\":\"Add two numbers together\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"number\",\"description\":\"First number\"},\"b\":{\"type\":\"number\",\"description\":\"Second number\"}}}}",
        "contentType": "application/json"
      }
    }
  }'
```

### 4. Run the sample

```bash
mvn quarkus:dev
```

### 5. Open the chat UI

Open http://localhost:8080 in your browser.

Try asking the bot:
- "Search for available MCP servers"
- "Connect to the calculator server"
- "What is 42 + 17?"
- "What time is it?"

## How It Works

The `Bot` AI service is configured with `ApicurioRegistryMcpTools` which provides three tools:

- **searchMcpServers**: Search the registry for available MCP servers by query
- **connectMcpServer**: Connect to an MCP server by its artifactId (and optionally groupId)
- **disconnectMcpServer**: Disconnect a previously connected MCP server

When the LLM receives a user request, it can autonomously search the registry for relevant
MCP servers, connect to them, and then use the tools those servers provide.

The tool definitions are stored as `MCP_TOOL` artifacts in Apicurio Registry following the
official MCP specification. Connection metadata (server URL, transport type) is stored as
artifact labels, keeping the artifact content spec-compliant.

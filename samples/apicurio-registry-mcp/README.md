# Apicurio Registry MCP Server Discovery Sample

This sample demonstrates how to use the Quarkus LangChain4j Apicurio Registry integration
to dynamically discover and connect to MCP servers registered in an Apicurio Registry instance.

## Prerequisites

- A running Apicurio Registry instance (e.g. via `docker run -p 8080:8080 quay.io/apicurio/apicurio-registry:latest-release`)
- MCP tool definitions registered as `MCP_TOOL` artifacts in the registry (following the MCP spec), with connection metadata stored as artifact labels (`mcp-server-url`, `mcp-transport-type`)
- An OpenAI API key (or configure a different model provider)

## Running

1. Start Apicurio Registry:
   ```bash
   docker run -p 8080:8080 quay.io/apicurio/apicurio-registry:latest-release
   ```

2. Set your OpenAI API key:
   ```bash
   export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=your-api-key
   ```

3. Run the sample:
   ```bash
   mvn quarkus:dev
   ```

4. Open http://localhost:8082 in your browser to interact with the chatbot.

## How It Works

The `Bot` AI service is configured with `ApicurioRegistryMcpTools` which provides three tools:

- **searchMcpServers**: Search the registry for available MCP servers
- **connectMcpServer**: Connect to an MCP server by its groupId and artifactId
- **disconnectMcpServer**: Disconnect a previously connected MCP server

When the LLM receives a user request, it can autonomously search the registry for relevant
MCP servers, connect to them, and then use the tools those servers provide.

# Assistant with dynamically created MCP connections

This application demonstrates how to create MCP clients dynamically based on what the user enters in the UI.
It contains two pages: one for adding/removing MCP clients and another for talking to an LLM.
When you use the chat page, tools from the configured MCP clients will always be made available to the LLM.

The application currently supports only a single application-scoped chat memory for simplicity.

**NOTE**: The application currently does not store any persistent data. All information about configured MCP clients is lost after a restart.

**NOTE**: Connections to both public and secured MCP servers can be added. Currently, secured MCP Servers can be imported only if they are compliant with the latest version of the MCP Authorization specification available at https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization.

## Running the Demo

### Start the application in dev mode:

```bash
mvn quarkus:dev -Dquarkus.langchain4j.openai.api-key=$API_KEY
```

The application will start on http://localhost:8080.

OpenAI is expected to be used as the LLM provider, hence the application expects the `quarkus.langchain4j.openai.api-key` property to be specified.
If you prefer to use another provider, remove the OpenAI extension, add your preferred provider's extension, 
and use the necessary configuration property with the API key (if necessary).

For example, add the `quarkus-langchain4j-ai-gemini` dependency to pom.xml

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-ai-gemini</artifactId>
    <version>${quarkus-langchain4j.version}</version>
</dependency>
```

and start the application in dev mode as follows:

```bash
mvn quarkus:dev -Dquarkus.langchain4j.ai.gemini.api-key=$API_KEY
```

### Quarkus MCP Server

If you would like, you can launch a Quarkus MCP Server with public and secured endpoints in the `dynamic-mcp-clients-server` folder, follow README.md in that folder to add secured and public Quarkus MCP server connections.

### Well-known MCP Servers

To add connections to other well-known MCP servers, follow the corresponding instructions.

For example, to add a GitHub Streamable HTTP MCP Server connection:

* Create a GitHub OAuth2 application, see https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app
* Set `GitHub` name, `https://api.githubcopilot.com/mcp/` URL, press `Add Connection`
* Enter your GitHub OAuth2 application's client id, secret, and choose one or more scopes from https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps; starting with the `read:user` scope is reasonable
* Press `Authenticate and Add Connection`, login to GitHub, a connection to the GitHub MCP server will be added
* Ask the assistant a question such as `What is my profile name`


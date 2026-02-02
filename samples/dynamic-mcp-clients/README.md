# Assistant with dynamically created MCP connections

This application demonstrates how to create MCP clients dynamically based on what the user enters in the UI.
It contains two pages: one for adding/removing MCP clients and another for talking to an LLM.
When you use the chat page, tools from the configured MCP clients will always be made available to the LLM.

The application currently supports only a single user and there is a single application-scoped chat memory for simplicity.

**NOTE**: The application does not store any persistent data. All information about configured MCP clients is lost after a restart. 

OpenAI is expected to be used as the LLM provider, hence the application expects the `quarkus.langchain4j.openai.api-key` property to be specified.
If you prefer to use another provider, remove the OpenAI extension, add your preferred provider's extension, 
and provide the necessary configuration property with the API key (if necessary).

## Running the Demo

### Start the application in dev mode:

```bash
mvn quarkus:dev -Dquarkus.langchain4j.openai.api-key=$API_KEY
```

The application will start on http://localhost:8080
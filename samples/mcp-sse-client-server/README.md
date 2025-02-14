# MCP-based client-server example using the SSE transport protocol

This sample showcases how to use a Quarkus MCP server to
provide tools to an LLM. In this case, we use the [SSE transport protocol](https://modelcontextprotocol.io/docs/concepts/transports#server-sent-events-sse) giving the LLM a set of tools to interact with for
weather forecast services. 

# Running the sample

Run the sample by starting the mcp server component in the `mcp-server` directory using `mvn quarkus:dev`.
This will start the server on port 8081. 

Then start the client component in the `mcp-client` directory using `mvn quarkus:dev`.

# Testing the service

Go to `http://localhost:8080` and interact with the chatbot (click the icon in the bottom left corner to open the chat
window).

You can also use the /alerts endpoint to get weather alerts for a specific state without interacting with a chatbot. For example, you can send a GET request to `/alerts?state=New York` to get alerts for the state of New York.
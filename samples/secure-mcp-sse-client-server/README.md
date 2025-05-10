# Secure MCP-based client-server example using the SSE transport protocol and Google authentication

This sample showcases how to login to Google and use the same authorization code flow access token to access both a Quarkus MCP server using the [SSE transport protocol](https://modelcontextprotocol.io/docs/concepts/transports#server-sent-events-sse) that requires authentication and Google AI Gemini model. 

Quarkus MCP server gives the LLM a tool that can return a name of the logged-in user. AI Gemini uses this tool to create a poem about Java for the logged-in user.

# Running the sample

Run the sample by starting the mcp server component in the `secure-mcp-server` directory using `mvn quarkus:dev`.
This will start the server on port 8081. 

Next, follow steps listed in the [Quarkus Google](https://quarkus.io/guides/security-openid-connect-providers#google) section to register an application with Google.
Name your Google application as `Quarkus LangChain4j AI`, and make sure an allowed callback URL is set to `http://localhost:8080/login`.
Google will generate a client id and secret, use them to set `quarkus.oidc.client-id` and `quarkus.oidc.credentials.secret` properties.

Then start the client component in the `secure-mcp-client` directory using `mvn quarkus:dev`.

# Testing the service

Go to `http://localhost:8080`, login with Google, request a poem and confirm that your name is returned alongside the poem about Java.

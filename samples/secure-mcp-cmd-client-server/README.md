# Secure MCP command line client-server example using the SSE transport protocol with Keycloak and AI Gemini.

This sample showcases how Quarkus MCP client can acquire OAuth2 client_credentials grant tokens from Keycloak and use them to access secure Quarkus MCP server using the [SSE transport protocol](https://modelcontextprotocol.io/docs/concepts/transports#server-sent-events-sse) protocol.

Quarkus MCP server gives the LLM a tool that can return a name of the service account. AI Gemini uses this tool to include a service account name in a poem about Java.

# Running the sample in dev mode

### MCP server

Start the mcp server component in the `secure-mcp-cmd-server` directory using `mvn quarkus:dev`.

This will start the server on port 8080 and launch a Keycloak container on port 8081. Keycloak dev service creates a `quarkus` realm with a `quarkus-mcp-server` client.

MCP server is protected by Quarkus OIDC and requires that all tokens that are sent to it have a `quarkus-mcp-server` audience. The MCP server also requires access to the protected REST server to complete the tool action and it is configured to exchange the incoming token for a token with the audience that targets the REST server, before propagating it to the REST server.

Keycloak `quarkus` realm configuration must be updated to support MCP server requirements.

### Keycloak configuration

Keycloak dev service has already created the `quarkus-mcp-server` client in the `quarkus` realm and is available on 8081 port. Go to `http://localhost:8081`, login as `admin:admin` and select the `quarkus` realm.

Create two more clients, `quarkus-mcp-client` that will represent Quarkus MCP client, and `quarkus-mcp-service` that will represent a protected REST server that the MCP `quarkus-mcp-server` server will call to complete the tool action.
Make sure both clients only have `Client Authentication` and  `Service Accounts Roles` client capabilities enabled.

Copy the secret of the `quarkus-mcp-client`, you will need it later to run Quarkus MCP client.

Create two `Optional` client scopes, `quarkus-mcp-server-scope` and `quarkus-mcp-service-scope`, and create the `Audience` mapper for each of these scopes, selecting `quarkus-mcp-server` and `quarkus-mcp-client` clients as included client audiences respectively.

Add `Optional` `quarkus-mcp-server` client scope to the `quarkus-mcp-client` client and `Optional` `quarkus-mcp-service` client scope to the `quarkus-mcp-server` client.

Finally, update the `quarkus-mcp-server` capabilities to support `Standard Token Exchange`. 

This Keycloak configuration enables Quarkus MCP client to request an access token that can be used to access the Quarkus MCP server only. It also allows Quarkus MCP server to exchange the token targeted at it for another token that will only be valid for accessing the protected REST server.

### MCP Client

Make sure the MCP server is started and the Keycloak configuration is done.

Get [AI Gemini API key](https://aistudio.google.com/app/apikey) and export it as an `AI_GEMINI_API_KEY` environment property:

```shell
export AI_GEMINI_API_KEY=your_ai_gemini_api_key
```

Export the Keycloak `quarkus-mcp-client` secret that you copied when configuring Keycloak as an `OIDC_CLIENT_SECRET` environment property:

```shell
export OIDC_CLIENT_SECRET=keycloak_quarkus_mcp_client_secret
```

Package the application and run it:

```shell
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```


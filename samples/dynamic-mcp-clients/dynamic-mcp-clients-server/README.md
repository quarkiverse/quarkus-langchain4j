# Quarkus MCP server that contains a public and secured Streamable HTTP endpoints

This Quarkus MCP server can be optionally used to support an assistant that must be launched in the `dynamic-mcp-clients-assistant` folder.

## Start the server in dev mode:

```bash
mvn quarkus:dev
```

To avoid an HTTP port conflict with the assistant server, the MCP server is started on the HTTP `8081` port.

Two streamable HTTP endpoints are created, a secured one at `http://localhost:8081/mcp` and a public one at `http://localhost:8081/echo/mcp`.

Keycloak container is also launched.

The secured endpoint at `http://localhost:8081/mcp` provides a tool that returns a name of the user who logged in into an authorization server associated with this endpoint.

When you will attempt to use an assistant to import `http://localhost:8081/mcp`, the assistant will request an authentication and ask to enter an OAuth2 Client ID and optional scopes. 

Enter an OAuth2 `alpha-client` Client ID.
Enter a `quarkus-mcp-alpha` scope - it must be done because currently Keycloak does not support a `resource` resource indicator.

Login as a user `alice` with a password `alice`.

The imported secured MCP server can now support assistant queries such as `What is the user name` ?

The public endpoint at `http://localhost:8081/echo/mcp` provides a tool that echoes a provided text.

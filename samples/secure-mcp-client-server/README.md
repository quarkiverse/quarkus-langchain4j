# Secure MCP client-server example using the Streamable HTTP transport protocol with GitHub OAuth2 and AI Gemini.

This sample showcases how to login with GitHub and use the GitHub authorization code flow access token to access secure Quarkus MCP server using the [Streamable HTTP transport](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http) protocol.

Quarkus MCP server gives the LLM a tool that can return a name of the logged-in user. AI Gemini uses this tool to create a poem about Java for the logged-in user.

# GitHub OAuth2 Application

Follow the [GitHub application registration](https://quarkus.io/guides/security-openid-connect-providers#github) process, and make sure to register the `http://localhost:8080/login` callback URL.

GitHub will generate a client id and secret. Use them to either set `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` environment properties or update the `quarkus.oidc.client-id=${github_client_id}` and `quarkus.oidc.credentials.secret=${github_client_secret}` properties in `application.properties` by replacing `${github_client_id}` with the generated client id and `${github_client_secret}` with the generated client secret.

By default, Quarkus GitHub provider submits the client id and secret in the HTTP Authorization header.
However, GitHub may require that both client id and secret are submitted as form parameters instead.

When you get HTTP 401 error after logging in to GitHub and being redirected back to Quarkus MCP server,
try to replace `quarkus.oidc.credentials.secret=${github.client.secret}` property
with the following two properties instead:

```properties
quarkus.oidc.credentials.client-secret.method=post
quarkus.oidc.credentials.client-secret.value=${github.client.secret}
```

# AI Gemini API key

Get [AI Gemini API key](https://aistudio.google.com/app/apikey). Use it to either set an `AI_GEMINI_API_KEY` environment property or update the `quarkus.langchain4j.ai.gemini.api-key=${ai_gemini_api_key}` property in `application.properties` by replacing `${ai_gemini_api_key}` with the API key value.

# Running the sample in dev mode

### MCP server

Start the mcp server component in the `secure-mcp-server` directory using `mvn quarkus:dev -Duser.name="your-github-account-name"`, for example: `mvn quarkus:dev -Duser.name="John Doe"`.

Note, you should use double quotes to register your GitHub account's full name

This will start the server on port 8081 and create a database containing a record with your GitHub account name and a `read:name` permission associated with this name.

### MCP Client

Make sure the GitHub OAuth2 application is registered, AI Gemini API key is available and the MCP server is started.

Start the client component in the `secure-mcp-client` directory using `mvn quarkus:dev`.

Go to `http://localhost:8080`, login with GitHub, request a poem and confirm that your name is returned alongside the poem about Java.

# Running the sample in prod mode

### MCP server

Start Postgresql:

```shell
docker run --rm=true --name quarkus_test -e POSTGRES_USER=quarkus_test -e POSTGRES_PASSWORD=quarkus_test -e POSTGRES_DB=quarkus_test -p 5432:5432 postgres:17.0
```

Package the mcp server component in the `secure-mcp-server` directory using `mvn clean package -Duser.name="your-github-account-name"`, for example: `mvn clean package -Duser.name="John Doe"`.

Note, you should use double quotes to register your GitHub account's full name

And run it:

```shell
java -jar target/quarkus-app/quarkus-run.jar
```

This will start the server on port 8081 and create a database containing a record with your GitHub account name and a `read:name` permission associated with this name.

### MCP Client

Make sure the GitHub OAuth2 application is registered, AI Gemini API key is available and the MCP server is started.

Package and run the mcp client component:

```shell
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

Go to `http://localhost:8080`, login with GitHub, request a poem and confirm that your name is returned alongside the poem about Java.

# Security

All HTTP requests to the Quarkus MCP server require authentication.

Additionally, the MCP server endpoint that provides access to tools requires that the security identity has a `read:name` permission.

Quarkus MCP server augments the identity created from a GitHub bearer access token to add the `read:name` permission to it. It does so by reading a database that contains a record with your GitHub account name and a posessed `read:name` permission.

Quarkus LangChain4J MCP Client accesses the GitHub access token acquired during the OAuth2 authorization code flow into Quarkus LangChain4J AI Poem service application. MCP client propagates this token as a bearer access token to access Quarkus MCP server.

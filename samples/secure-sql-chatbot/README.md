# Secure chatbot using advanced RAG and a SQL database

## Secure Movie Muse

This example demonstrates how to create a secure chatbot with RAG using
`quarkus-langchain4j`. This chatbot internally uses LLM-generated SQL
queries to retrieve the relevant information from a PostgreSQL database.

### Setup

The demo requires that your Google account's full name and email are configured.
You can use system or env properties, see `Running the example` section below.

When the application starts, a registered user, the movie watcher, is allocated a random preferred movie genre .

The setup is defined in the [Setup.java](./src/main/java/io/quarkiverse/langchain4j/samples/chatbot/Setup.java) class.

The registered movie watchers are stored in a PostgreSQL database. When running the demo in dev mode (recommended), the database is automatically created and populated.

The genre preferred by the registered movie watcher is used by a Movie `ContentRetriever` to sort the list of movies.

## Running the example

Users must authenticate with Google before they can start working with the Movie Muse. 
They also must register their OpenAI API key.

### Google Authentication

In order to register an application with Google, follow steps listed in the [Quarkus Google](https://quarkus.io/guides/security-openid-connect-providers#google) section.
Name your Google application as `Quarkus LangChain4j AI`, and make sure an allowed callback URL is set to `https://localhost:8443/login`.
Google will generate a client id and secret, use them to set `quarkus.oidc.client-id` and `quarkus.oidc.credentials.secret` properties.

### OpenAI API key

You must provide your OpenAI API key:

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

Then, simply run the project in Dev mode:

```shell
mvn quarkus:dev -Dname="Firstname Familyname" -Demail=someuser@gmail.com
```

Note, you should use double quotes to register your Google account's full name.

## Using the example

Open your browser and navigate to `https://localhost:8443`, accept the demo certificate.
Now, choose a `Login with Google` option.
Once logged in, click an orange robot icon in the bottom right corner to open a chat window.
Ask questions such as `Give me movies with a rating higher than 8`.

The chatbot is available at the secure `wss:` scheme.

It uses a SQL database with information about movies with their
basic metadata (the database is populated with data from
`src/main/resources/data/movies.csv` at startup). When you ask a question, an
LLM is used to generate SQL queries necessary for answering your question.
Check the application's log, the SQL queries and the retrieved data will be
printed there.

The chatbot will refer to you by your name during the conversation.

## Security Considerations

### HTTPS and WSS

This demo requires the use of secure HTTPS and WSS WebSockets protocols only.

### Chatbot is accessible to authenticated users only

Only users who have authenticated with Google will see a page which contains a chatbot icon.
It eliminates a risk of non-authenticated users attempting to trick LLM.  

### CORS

Even though browsers do not enforce Single Origin Policy (SOP) for WebSockets HTTP upgrade requests, enabling
CORS origin check can add an extra protection in combination with verifying the expected authentication credentials.

For example, attackers can set `Origin` themselves, but they will not have the HTTPS bound authentication session cookie
which can be used to authenticate a WSS WebSockets upgrade request.
Or if the authenticated user is tricked into visiting an unfriendly website, then a WSS WebSockets upgrade request will fail
at the Quarkus CORS check level.

### Custom WebSocket ticket system

In addition to requiring authentication over secure HTTPS, in combination with the CORS constraint for
the HTTP upgrade to succeed, this demo shows a simple WebSockets ticket system to verify that the current HTTP upgrade request
was made from the page allocated to authenticated users only.

When the user completes the OpenId Connect Google authentication, a dynamically generated HTML page will contain a WSS HTTP upgrade link 
with a randomly generated ticket added at the authentication time, for example: `wss://localhost/chatbot?ticket=random_ticket_value`.

HTTP upgrade checker will ensure that a matching ticket is found before permitting an upgrade.

### User identity is visible to AI services and tools

AI service and tools can access an ID `JsonWebToken` token which represents a successful user authentication and use it to complete its work.

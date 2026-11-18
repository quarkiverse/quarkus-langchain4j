# Testing an AI service with the Mock Model API

This example shows how to test an application that uses an AI service without a real model, an API key or network
access, with the `quarkus-langchain4j-mock-api` extension. The mock starts as a Dev Service, every model of the
application is pointed at it, the tests script what the model answers, and assert on what the application sent.

The application is a small support assistant (`SupportAssistant`) with a tool that looks orders up (`OrderTools`) and
a method that triages a message into a structured `Triage` record. `SupportResource` exposes both over REST.

## Running the tests

```shell
mvn test
```

No configuration is needed: the mock is enabled for the `test` profile in `application.properties`, and it fills in
the missing OpenAI API key with a placeholder.

`SupportAssistantTest` covers the situations a test of an AI service usually needs:

* **a scripted answer**: `mock.chat().reply("...")`, then assert on the system message, the user message, the model
  name and the tool definitions that reached the model (`mock.chat().singleRequest().asChat()`);
* **a tool call**: `mock.chat().replies(List.of(ChatReply.toolCall("trackOrder", ...), ChatReply.text("...")))`. The
  first reply makes the AI service run `OrderTools.trackOrder`, and the second request carries the tool's result
  back to the model, where the test reads it as a message with the `tool` role;
* **structured output**: the reply is the JSON of a `Triage`, and the request carries the JSON schema the extension
  derived from the record;
* **an error**: `mock.chat().fail(429, "rate limited")` answers in OpenAI's error format, and the endpoint fails.

The mock is reset before each test method, so tests do not see each other's replies or requests.

## Dev mode

The mock is also enabled for the `dev` profile, so the application runs without an API key:

```shell
mvn quarkus:dev
curl -X POST localhost:8080/support/chat -d 'Hi'
```

With nothing queued, the mock answers with `quarkus.langchain4j.mock-api.default-chat-reply`. The Dev UI shows the
mock among the Dev Services, with its URL; use its admin API to script replies and read the captured requests:

```shell
URL=<the mock's URL>
# queue a reply
curl -X POST $URL/_admin/replies/chat -H 'Content-Type: application/json' \
     -d '[{"kind":"ChatReply","text":"Your order ORD-1001 was shipped."}]'
curl -X POST localhost:8080/support/chat -d 'Where is ORD-1001?'
# the requests captured so far
curl $URL/_admin/requests
# clear queued replies and captured requests
curl -X POST $URL/_admin/reset
```

The extension is a `provided` dependency, so it is on the classpath in dev mode and in the tests, but not in the
packaged application, which talks to OpenAI with `QUARKUS_LANGCHAIN4J_OPENAI_API_KEY`.

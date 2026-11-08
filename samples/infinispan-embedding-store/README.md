# Infinispan embedding store sample

This sample demonstrates how to use [`quarkus-langchain4j-infinispan`](../../embedding-stores/infinispan)
as a vector store for semantic search.

It ingests a few bundled text snippets into Infinispan and exposes two REST
endpoints to inspect and search them by meaning. Embeddings are produced with
the OpenAI embedding model (any model providing 1536-dimensional vectors works).

## Run it

Set your OpenAI API key, then start the dev mode:

```bash
export OPENAI_API_KEY=sk-...
./mvnw -pl samples/infinispan-embedding-store quarkus:dev
```

> Infinispan is started automatically by Quarkus dev services (a Docker
> container), so **Docker must be installed and running**. To use an existing
> Infinispan server instead, set `quarkus.infinispan-client.server-list` and
> disable dev services.

Then, in another terminal:

```bash
# Load the bundled sample documents into Infinispan
curl http://localhost:8080/documents/ingest

# Search by meaning
curl "http://localhost:8080/documents/search?q=how%20do%20I%20store%20vectors%20in%20memory"
```

## Configuration

`src/main/resources/application.properties`:

```properties
quarkus.langchain4j.infinispan.dimension=1536
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY:YOUR_OPENAI_API_KEY}
quarkus.infinispan-client.devservices.enabled=true
```

`dimension` is required and **must match the embedding model**
(1536 for the default OpenAI model). Dev services start the Infinispan server
automatically; for production use disable dev services
(`quarkus.infinispan-client.devservices.enabled=false`) and point
`quarkus.infinispan-client.server-list` at your own server.

## Endpoints

| Method | Path                 | Description                                              |
|--------|----------------------|----------------------------------------------------------|
| GET    | `/documents/ingest`  | Loads the bundled sample documents into Infinispan      |
| GET    | `/documents/search`  | `?q=<text>&max=<n>` returns the n most similar documents |

## Notes

This sample intentionally keeps the embedding model pluggable: swap
`quarkus-langchain4j-openai` for any other Quarkus LangChain4j embedding-model
extension (e.g. an ONNX-based local model) and update `dimension` accordingly.

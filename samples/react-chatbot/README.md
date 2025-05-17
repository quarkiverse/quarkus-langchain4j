# Chatbot example

This example demonstrates how to create a simple react-chatbot using `quarkus-langchain4j`.

## Running the example

A prerequisite to running this example is to provide your watsonx.ai API key.

```
export QUARKUS_LANGCHAIN4J_WATSONX_API_KEY=<your-watsonx-api-key>
export QUARKUS_LANGCHAIN4J_WATSONX_PROJECT_ID=<your-project-id>
export QUARKUS_LANGCHAIN4J_WATSONX_BASE_URL=<your-base-url>
```

Then, simply run the project in `dev` mode:

```
mvn quarkus:dev
```

## Using the example

You can interact with the chatbot by sending POST requests to:

POST http://localhost:8080/react/

Example using curl:

```
curl -X POST http://localhost:8080/react/ \
  -H "Content-Type: application/json" \
  -d 'Who is the current pope?'
```

or

```
curl -X POST http://localhost:8080/react/ \
  -H "Content-Type: application/json" \
  -d 'What did the critic mean about Expedition 33? Tell me what PC Gamer and IGN magazines think.'
```

You can also use Postman or any other HTTP client to make the request.
# Review triage example

This example demonstrates how to a sentiment-analyzing AI service
with `quarkus-langchain4j`.

## Running the example

A prerequisite to running this example is to provide your OpenAI API key.

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

Then, simply run the project in Dev mode:

```
mvn quarkus:dev
```

## Using the example

Open your browser and navigate to http://localhost:8080. The application
acts as a bank's robot that accepts new reviews written by clients, analyzes
their sentiment and thinks up an appropriate response to the client. Simply
write something positive (`Your services are great.`) or negative (`You are
thieves!`) into the form and click Submit.


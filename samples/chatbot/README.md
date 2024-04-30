# Chatbot example

This example demonstrates how to create a simple chatbot with RAG using `quarkus-langchain4j`.

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

Open your browser and navigate to http://localhost:8080. Click the red robot
in the bottom right corner to open the chat window.

The chatbot is a conversational agent that uses information from the files
in `src/main/resources/catalog` to answer your questions about banking
products. More information about how it works is shown on the webpage.
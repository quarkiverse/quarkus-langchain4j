# Financial Assistant Chatbot with Easy RAG

This example demonstrates how to create a financial assistant chatbot with Retrieval Augmented Generation (RAG) using
`quarkus-langchain4j` and Kotlin, specifically utilizing the Easy RAG extension.
For more information about Easy RAG, refer to the file 
`docs/modules/ROOT/pages/easy-rag.adoc`.

## Running the example

A prerequisite to running this example is to provide your OpenAI API key.
        
You may either set the environment variable:
```shell
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```
or create an `.env` file in the root of the project with the following content:
```dotenv
QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

Then, simply run the project in Dev mode:

```
mvn quarkus:dev
```

## Using the example

Open your browser and navigate to http://localhost:8080. Click the red robot
in the bottom right corner to open the chat window.

The chatbot is a financial assistant that:
1. Answers questions about financial products using information retrieved from documents
2. Provides current stock prices for selected companies (AAPL, GOOG, MSFT)
3. Analyzes sentiment in user messages
4. Content moderation: Detects malicious content in user messages and sends a warning by email, if detected

### Setting up the document catalog

The app is configured to look for your financial product documents in a `catalog` directory relative to the current working directory.

```
mkdir -p src/main/resources/catalog
# Add your financial product documents (PDF, TXT, etc.) to this directory
```

The application will use the Easy RAG extension to process these documents and retrieve relevant information when answering questions.

## Using other model providers

### Compatible OpenAI serving infrastructure

Add `quarkus.langchain4j.openai.base-url=http://yourerver` to `application.properties`.

In this case, `quarkus.langchain4j.openai.api-key` is generally not needed.

### Ollama


Replace:

```xml
        <dependency>
            <groupId>io.quarkiverse.langchain4j</groupId>
            <artifactId>quarkus-langchain4j-openai</artifactId>
            <version>${quarkus-langchain4j.version}</version>
        </dependency>
```

with

```xml
        <dependency>
            <groupId>io.quarkiverse.langchain4j</groupId>
            <artifactId>quarkus-langchain4j-ollama</artifactId>
            <version>${quarkus-langchain4j.version}</version>
        </dependency>
```

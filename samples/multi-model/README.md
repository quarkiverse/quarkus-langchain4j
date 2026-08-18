# Multiple models example

This example demonstrates how to create a simple chatbot whose behavior can be tuned on AI model in use   

## Running the example

A prerequisite to running this example is to provide your OpenAI API key.

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

Then, simply run the project in Dev mode:

```
mvn quarkus:dev -Dquarkus.profile=openai
```

## Using the example

```
curl -XPOST -d'What is the height of Everest mountain?' localhost:8080/chat
```

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

and (optionally) add the following parameters to application.properties:
```properties
quarkus.langchain4j.ollama.chat-model.temperature=0
quarkus.langchain4j.ollama.model-name=mistral
```

The same can be done for other providers, see the documentation: https://docs.quarkiverse.io/quarkus-langchain4j/dev/models.html

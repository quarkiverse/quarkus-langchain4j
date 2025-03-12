# Image to PlantUML example

This example demonstrates how to create a CLI-based `Image to PlantUML`
converter application powered by an LLM.

The example also demonstrates the use of Guardrails for validating output,
ensuring the tool only generates correct code.

## Preparing to run the example

A prerequisite to running this example is to provide your OpenAI API key.

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

## Using the example in interactive mode

Compile the project and run it:

```
mvn package
java -jar target/quarkus-app/quarkus-run.jar 
```

Optionally, specify a target language for the translation using `-l`, this
defaults to English. Then, simply write something in your language and
always leave an empty line to get the translation.

## Using the example in command mode

You can also specify a file to translate by appending it as the program's argument,
for example:

```
mvn package
java -jar target/quarkus-app/quarkus-run.jar examples/qia-1-5.webp
java -jar target/quarkus-app/quarkus-run.jar examples/qia-1-6.webp
java -jar target/quarkus-app/quarkus-run.jar examples/qia-1-7.webp
```

This also works with Dev mode:
```
mvn quarkus:dev -Dquarkus.args="examples/qia-1-5.webp
mvn quarkus:dev -Dquarkus.args="examples/qia-1-6.webp
mvn quarkus:dev -Dquarkus.args="examples/qia-1-7.webp
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

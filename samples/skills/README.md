# Skills example

This example demonstrates how to use the Skills feature of Quarkus LangChain4j.
Skills are markdown files that provide additional instructions to an AI service,
which can be activated on demand by the LLM. In this example, a `poem-writing`
skill instructs the LLM to write a 12-line poem about a sad mushroom.

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

Execute:

```
curl http://localhost:8080/poem
```

and you should get a 12-line poem about a sad mushroom, as dictated by the
`poem-writing` skill defined in `src/main/resources/skills/poem-writing/SKILL.md`.

## Using other model providers

### Compatible OpenAI serving infrastructure

Add `quarkus.langchain4j.openai.base-url=http://yourserver` to `application.properties`.

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

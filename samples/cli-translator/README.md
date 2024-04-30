# CLI Translator example

This example demonstrates how to create a CLI-based translator application
powered by an LLM.

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
java -jar target/quarkus-app/quarkus-run.jar examples/french.txt
```

This also works with Dev mode:
```
mvn quarkus:dev -Dquarkus.args="examples/french.txt"
```
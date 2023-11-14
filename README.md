# Quarkiverse - Quarkus Langchain4j

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.langchain4j/quarkus-langchain4j?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.langchain4j/quarkus-langchain4j)

This repository contains Quarkus extensions that facilitate seamless integration between Quarkus and [LangChain4j](https://github.com/langchain4j/langchain4j), enabling easy incorporation of Large Language Models (LLMs) into your Quarkus applications.

## Features

Here is a non-exhaustive list of features that are currently supported:

- Declarative AI services
- Integration with diverse LLMs (OpenAI GPTs, Hugging Faces, Ollama...)
- Tool support
- Embedding support
- Document store integration (Redis, Chroma...)
- Native compilation support
- Integration with Quarkus observability stack (metrics, tracing...)

## Documentation

Refer to the comprehensive [documentation](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/docs) for detailed information and usage guidelines.

## Samples

Check out the [samples](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/docs) and [integration tests](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/integration-tests) to gain practical insights on how to use these extensions effectively.

## Getting Started

To incorporate Quarkus Langchain4j into your Quarkus project, add the following Maven dependency:

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-openai</artifactId>
    <version>{latest-version}</version>
</dependency>
```

or, to use hugging face:

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-huggingface</artifactId>
    <version>{latest-version}</version>
</dependency>
```

Make sure to replace `{latest-version}` with the most recent release version available on [Maven Central](https://search.maven.org/artifact/io.quarkiverse.langchain4j/quarkus-langchain4j).

## Contributing

Feel free to contribute to this project by submitting issues or pull requests.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.



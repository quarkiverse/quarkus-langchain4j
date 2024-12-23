# AI Service example with Tools that can write a poem and send it by email

This example demonstrates how to create an AI service that can discover
and use locally implemented tools.

## Running the example

A prerequisite to running this example is to provide your OpenAI API key.

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

Then, simply run the project in Dev mode:

```
mvn quarkus:dev
```

> **_NOTE:_**
>  When demoing observability is desired, execute `mvn quarkus:dev -Dobservability`

## Using the example

Open the application at http://localhost:8080 and click `Send me an email`.

Quarkus will use a mock mailer which simply logs the email on the terminal.  

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

## Viewing the sent email

Go to the DevUI and click on the Mailpit UI      

## Viewing traces

> **_NOTE:_**
>  For this to be applicable, the application has to have been started using `mvn quarkus:dev -Dobservability`

The application has been configured to start the LGTM stack via [Dev Service](https://quarkus.io/guides/observability-devservices-lgtm).

Find the host port on which Grafana is running by executing:

```
GRAFANA_PORT=$(docker inspect $(docker container ls -q --filter 'label=quarkus-dev-service-lgtm=quarkus') --format '{{index (index (index .NetworkSettings.Ports "3000/tcp") 0) "HostPort"}}')
echo http://localhost:$GRAFANA_PORT
```

Open your browser at `http://localhost:${GRAFANA_PORT}`

When prompted to log in, use `admin:admin`  as the username / password combination.

From the menu on the top left, click on `Explore`. On the page, select `Tempo` as the datasource (next to `Outline`), then go to `Query type`, select `Search` and select `quarkus-langchain4j-sample-poem` from the dropdown options of `Service Name`.
Now hit `Run query` in the top right corner.

## Viewing metrics

Simply open the application at http://localhost:8080/q/metrics 


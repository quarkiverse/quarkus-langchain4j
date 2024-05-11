# AI Service example with Tools that can write a poem and send it by email

This example demonstrates how to create an AI service that can discover
and use locally implemented tools.

## Running the example

A prerequisite to running this example is to provide your OpenAI API key.

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

To allow the application to send emails, start a mock SMTP server (replace
Podman with your own container runtime):

```
podman run -p 8025:8025 -p 1025:1025 docker.io/mailhog/mailhog
```

Then, simply run the project in Dev mode:

```
mvn quarkus:dev
```

## Using the example

Open the UI of the mock SMTP server at http://localhost:8025. This is where any
emails sent by the robot will appear.

To have the robot write a poem and send it to `sendMeALetter@quarkus.io` (the
actual address doesn't matter, for any address it will simply appear in the
SMTP server's UI), execute:

```
curl http://localhost:8080/email-me-a-poem
```

If you don't have curl or a similar tool, simply opening the URL in your web
browser will work too. After this is done, open the SMTP server's UI and you
will see the email with a poem about Quarkus.


# Secure Vertex AI Gemini Poem Demo

This demo showcases the implementation of a secure Vertex AI Gemini Poem Demo which is available only to users authenticated with Google.

## The Demo

Demo asks Vertex AI Gemini LLM to write a short 1 paragraph poem, using the access token acquired during the OIDC authorization code flow

### Setup

This demo requires users to authenticate with Google.
All you need to do is to register an application with Google, follow steps listed in the [Quarkus Google](https://quarkus.io/guides/security-openid-connect-providers#google) section.
Name your Google application as `Quarkus LangChain4j AI`, and make sure an allowed callback URL is set to `http://localhost:8080/login`.
Google will generate a client id and secret, use them to set `quarkus.oidc.client-id` and `quarkus.oidc.credentials.secret` properties:

```properties
quarkus.oidc.provider=google
quarkus.oidc.client-id=${GOOGLE_CLIENT_ID}
quarkus.oidc.credentials.secret=${GOOGLE_CLIENT_SECRET}
quarkus.oidc.authentication.extra-params.scope=https://www.googleapis.com/auth/generative-language.retriever,https://www.googleapis.com/auth/cloud-platform
quarkus.oidc.authentication.redirect-path=/login

# See https://cloud.google.com/vertex-ai/docs/general/locations
vertex-ai-region=europe-west2

quarkus.langchain4j.vertexai.gemini.location=https://${vertex-ai-region}-aiplatform.googleapis.com
quarkus.langchain4j.vertexai.gemini.project-id=${GOOGLE_PROJECT_ID}
```

You must enable Vertex AI API in your Google Cloud project.

### AI Service

This demo leverages the AI service abstraction, with the interaction between the LLM and the application handled through the AIService interface.

The `io.quarkiverse.langchain4j.sample.PoemAiService` interface uses specific annotations to define the LLM:

```java
package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface PoemAiService {

    /**
     * Ask the LLM to create a poem about Enterprise Java.
     *
     * @return the poem
     */
    @SystemMessage("You are a professional poet")
    @UserMessage("""
            Write a short 1 paragraph poem about Java. Set an author name to the model name which created the poem.
            """)
    String writeAPoem();

}

### Using the AI service

Once defined, you can inject the AI service as a regular bean, and use it:

```java
package io.quarkiverse.langchain4j.sample;

import java.net.URISyntaxException;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/poem")
@Authenticated
public class PoemResource {

    private final PoemAiService aiService;

    public PoemResource(PoemAiService aiService) throws URISyntaxException {
        this.aiService = aiService;
    }

    @GET
    public String getPoem() {
        return aiService.writeAPoem();
    }
}

```

`PoemResource` can only be accessed by authenticated users.

## Security Considerations

This demo makes it possible to access Google Vertex AI API enabled in the Google Cloud project only to users who:

* Authenticated to Quarkus REST PoemService with Google using OIDC authorization code flow.
* Authorized `Quarkus LangChain4j AI` application registered in the Google Cloud project to use the access token to access Google Generative API on behalf of the currently authentiicated user. This authorization is requested from users during the authentication process and is configured by adding additional `quarkus.oidc.authentication.extra-params.scope=https://www.googleapis.com/auth/generative-language.retriever,https://www.googleapis.com/auth/cloud-platform` scopes in the application properties.
* Quarkus LangChain4j vertex-ai-gemini model provider uses this authorized token on behalf of the current user to access Google Vertex AI endpoint.

## Running the Demo

To run the demo, use the following commands:

```shell
mvn quarkus:dev
```

Then, access `http://localhost:8080`, login to Google, and follow a provided application link to read the poem.


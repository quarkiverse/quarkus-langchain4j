# Secure Vertex AI Gemini Poem Demo

This demo showcases the implementation of a secure Azure OpenAI Poem Demo which is available only to users authenticated with Azure.

## The Demo

### Setup

The demo asks Azure OpenAI LLM to write a short 1 paragraph poem, using the access token acquired during the OIDC authorization code flow.

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

## Azure Authentication

This demo requires users to authenticate with Azure OpenAI.
All you need to do is to register an application with Azure, follow steps listed in the [Quarkus Microsoft](https://quarkus.io/guides/security-openid-connect-providers#microsoft) section, please keep in mind Azure Active Directory is now called Microsoft Entra ID.
Name your application as `Quarkus LangChain4j AI`, and make sure an allowed callback URL is set to `http://localhost:8080/login`.
Entra ID will generate a client id and secret, use them to set `quarkus.oidc.client-id` and `quarkus.oidc.credentials.secret` properties:

```properties
quarkus.oidc.auth-server-url=https://login.microsoftonline.com/${AZURE_TENANT_ID}
quarkus.oidc.application-type=web-app
quarkus.oidc.token-state-manager.split-tokens=true 
quarkus.oidc.client-id=${AZURE_CLIENT_ID}
quarkus.oidc.credentials.secret=${AZURE_CLIENT_SECRET}
quarkus.oidc.authentication.extra-params.scope=${AZURE_OPENAI_SCOPES}
quarkus.oidc.authentication.redirect-path=/login

quarkus.langchain4j.azure-openai.resource-name=${AZURE_OPENAI_RESOURCE}
quarkus.langchain4j.azure-openai.deployment-name=${AZURE_OPENAI_DEPLOYMENT}
quarkus.langchain4j.azure-openai.log-requests=true
quarkus.langchain4j.azure-openai.log-responses=true
quarkus.langchain4j.azure-openai.use-security-identity-token=true
```

You must enable Azure OpenAI in your Azure tenant.

## Security Considerations

This demo makes it possible to access Azure OpenAI API enabled in the Azure tenant dashboard only to users who:

* Authenticated to Quarkus REST PoemService with Microsoft Entra ID using OIDC authorization code flow.
* Authorized `Quarkus LangChain4j AI` application registered in Microsoft Entra ID to use the access token to access Azure OpenAI on behalf of the currently authentiicated user. This authorization is requested from users during the authentication process and is configured by adding `quarkus.oidc.authentication.extra-params.scope` in the application properties.
* Quarkus LangChain4j azure-openai model provider uses this authorized token on behalf of the current user to access Azure OpenAI endpoint.

## Running the Demo

To run the demo, use the following commands:

```shell
mvn quarkus:dev
```

Then, access `http://localhost:8080`, login to Microsoft Entra ID, and follow a provided application link to read the poem.


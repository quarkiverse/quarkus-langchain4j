# Secure Vertex AI Gemini and Azure OpenAI Poem Demo

This advanced secure poem demo showcases how users authenticated with Google can request a poem from a Vertex AI Gemini model and users authenticated with Microsoft Entra ID can request a poem from an Azure OpenAI model.

## The Demo

Demo asks either Vertex AI Gemini or Azure OpenAI LLM to write a short 1-paragraph poem, using the access token acquired during the OIDC authorization code flow with either Google or Microsoft Entra ID OpenId Connect provider.

### OpenId Connect authentication

This demo requires users to authenticate with either Google or Microsoft Entra ID.

#### Google authentication

You have to register an application with Google, follow steps listed in the [Quarkus Google](https://quarkus.io/guides/security-openid-connect-providers#google) section.

Name your Google application as `Quarkus LangChain4j AI`, and make sure an allowed callback URL is set to `http://localhost:8080/model/vertex-gemini`.
Google will generate a client id and secret, use them to set `quarkus.vertex-gemini.oidc.client-id` and `quarkus.vertex-gemini.oidc.credentials.secret` properties.
Set `GOOGLE_PROJECT_ID` to the id of your Google Cloud project.
You must also enable Vertex AI API in your Google Cloud project.

```properties
# Named 'vertex-gemini' Google OIDC provider
quarkus.oidc.vertex-gemini.provider=google
quarkus.oidc.vertex-gemini.client-id=${GOOGLE_CLIENT_ID}
quarkus.oidc.vertex-gemini.credentials.secret=${GOOGLE_CLIENT_SECRET}
quarkus.oidc.vertex-gemini.authentication.extra-params.scope=https://www.googleapis.com/auth/generative-language.retriever,https://www.googleapis.com/auth/cloud-platform
quarkus.oidc.vertex-gemini.authentication.redirect-path=/model/vertex-gemini

vertex-ai-region=europe-west2
quarkus.langchain4j.vertexai.gemini.location=${vertex-ai-region}
quarkus.langchain4j.vertexai.gemini.project-id=${GOOGLE_PROJECT_ID}
```

#### Microsoft Entra ID authentication

Please follow [Get started with Azure OpenAI service](https://learn.microsoft.com/en-us/azure/ai-services/openai/overview#get-started-with-azure-openai-service) guide, note that you must complete a Limited Access Registration Form first before you can start creating Azure OpenAI service resources.

Once an approval to work with Azure OpenAI service is given, create your first Azure OpenAI resource such as `quarkus-langchain4j`.
Use Azure OpenAI Studio to create a deployment, by selecting one of the base models such as `gpt-4o-mini`, note the API version of selected model.

Go to `Access Control (IAM)` in the `quarkus-langchain4j` resource dashboard, choose `Role Assignments`, add `Azure AI Developer` to yourself (or try one of the Cognitive Data Service roles). Repeat for all other users which are allowed to access the resource.

Next, register an Azure Entra ID OIDC application.

Name your Azure Entra ID application as `Quarkus LangChain4j AI`, and make sure an allowed callback URL is set to `http://localhost:8080/model/azure-openai`.
Entra ID will generate a client id and secret, use them to set `quarkus.oidc.azure-openai.client-id` and `quarkus.oidc.azure-openai.credentials.secret` properties. Also note a tenant id.

Finally, go to `API Permissions` in the registered `Quarkus LangChain4j AI` application dashboard.
Select `Add Permission`, next select `APIs My Orgnaization uses` and choose `Microsoft Cognitive Services` and enable its `user_impersonation` option.

```properties
# Named 'azure-openai' Microsoft Entra ID OIDC provider
quarkus.oidc.azure-openai.auth-server-url=https://login.microsoftonline.com/${AZURE_TENANT_ID}/v2.0
quarkus.oidc.azure-openai.application-type=web-app
quarkus.oidc.azure-openai.client-id=${AZURE_CLIENT_ID}
quarkus.oidc.azure-openai.credentials.secret=${AZURE_CLIENT_SECRET}
quarkus.oidc.azure-openai.authentication.scopes=profile,https://cognitiveservices.azure.com/.default
quarkus.oidc.azure-openai.authentication.redirect-path=/model/azure-openai
quarkus.oidc.azure-openai.token.principal-claim=name
```

### Multiple models

This demo enables both Vertex AI Gemini and Azure OpenAI models.
When more than one model is used, you must use `quarkus.langchain4j.chat-model.provider` to name the default model's provider.

#### Vertex AI Gemini

Vertex AI Gemini is a default model and is configured as follows:

```properties
# Default Vertex AI Gemini model is accessed after a user has authenticated with Google.
# See https://cloud.google.com/vertex-ai/docs/geeral/locations
vertex-ai-region=europe-west2

quarkus.langchain4j.chat-model.provider=vertexai-gemini
quarkus.langchain4j.vertexai.gemini.location=${vertex-ai-region}
quarkus.langchain4j.vertexai.gemini.project-id=${GOOGLE_PROJECT_ID}
quarkus.langchain4j.vertexai.gemini.log-requests=true
quarkus.langchain4j.vertexai.gemini.log-responses=true
```

#### Azure OpenAI

Azure OpenAI model is configured using an `openai` named configuration:

```properties
# Named Azure OpenAI model is accessed after a user has authenticated with Entra ID.
quarkus.langchain4j.openai.chat-model.provider=azure-openai
quarkus.langchain4j.azure-openai.openai.resource-name=${AZURE_OPENAI_RESOURCE}
quarkus.langchain4j.azure-openai.openai.deployment-name=${AZURE_OPENAI_DEPLOYMENT}
quarkus.langchain4j.azure-openai.openai.log-requests=true
quarkus.langchain4j.azure-openai.openai.log-responses=true
```

### ChatLanguageModel

This demo leverages ChatLanguageModel instead of the AI service abstraction to simplify managing multiple models, with the interaction between the LLM and the application handled through the ChatLanguageModel interface.

```java
package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/poem")
@Authenticated
public class PoemResource {

    static final UserMessage USER_MESSAGE = 
        UserMessage.from("Write a short 1 paragraph poem about Java. Set an author name to the model (or deployment) name which created the poem.");
    
    @Inject
    ChatLanguageModel vertexAiGemini;
    
    @Inject
    @ModelName("openai")
    ChatLanguageModel azureOpenAI;
    
    @GET
    @Path("vertex-gemini")
    public String getPoemGemini() {
        Response<AiMessage> response = vertexAiGemini.generate(USER_MESSAGE);
        return response.content().text();
    }
        
    @GET
    @Path("azure-openai")
    public String getPoemAzureOpenAI() {
        Response<AiMessage> response = azureOpenAI.generate(USER_MESSAGE);
        return response.content().text();
    }
    
}

`PoemResource` can only be accessed by authenticated users from an HTML page generated after a successful authentication. 
It uses either Vertex AI Gemini or Azure OpenAI model to generate a poem.

## Security Considerations

This demo makes it possible to access Google Vertex Gemini or Azure OpenAI models only to users who have authenticated with either Google or Microsoft Entra ID and authorized the registered `Quarkus LangChain4j AI` application to access either of these models on behalf of the currently authenticated user.

### Google Vertex AI Gemini

Users authorize `Quarkus LangChain4j AI` application registered in the Google Cloud project to use the access token to access Google Generative API on behalf of the currently authenticated user. This authorization is requested from users during the authentication process and is configured by adding additional `quarkus.oidc.vertex-gemini.authentication.extra-params.scope=https://www.googleapis.com/auth/generative-language.retriever,https://www.googleapis.com/auth/cloud-platform` scopes in the application properties.
* Quarkus LangChain4j vertex-ai-gemini model provider uses this authorized token on behalf of the current user to access Google Vertex AI endpoint.

### Azure OpenAI

Users authorize `Quarkus LangChain4j AI` application registered in the Azure Entra ID dashboard to use the access token to access Azure OpenAI Generative API on behalf of the currently authenticated user. This authorization is requested from users during the authentication process and is configuring the following scopes:  `quarkus.oidc.azure-openai.authentication.scopes=profile,https://cognitiveservices.azure.com/.default` scopes in the application properties.
* Quarkus LangChain4j azure-openai model provider uses this authorized token on behalf of the current user to access Azure OpenAI endpoint.

## Running the Demo

To run the demo, use the following commands:

```shell
mvn quarkus:dev
```

Access `http://localhost:8080`, login to Quarkus PoemResource using either Google or Microsoft Entra ID, and follow a provided application link to read the poem.
Use the logout link to log out and try another OpenId Connect provider and model. For example, if you've started with Google and Vertex AI Gemini, try Microsoft Entra ID and Azure OpenAI next, or vice versa.

You do not have to have both Google and Microsoft Entra ID accounts enabled in order to run this demo.

Running it with only Google or Microsoft Entra ID authentication is sufficient in order to learn how a user authenticated to Quarkus with an OpenId Connect (OIDC) provider can authorize Quarkus AI service to access a remote LLM which is enabled in this user's account.


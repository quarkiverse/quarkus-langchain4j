package io.quarkiverse.langchain4j.sample;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import jakarta.inject.Inject;

public class ServiceAccountNameProvider {

    @RestClient
    @Inject
    ServiceAccountNameRestClient serviceAccountNameRestClient;

    @Tool(name = "sevice-account-name-provider", description = "Provides a name of the current service account")
    TextContent provideServiceAccountName() {
        return new TextContent(serviceAccountNameRestClient.getServiceAccountName());
    }
}

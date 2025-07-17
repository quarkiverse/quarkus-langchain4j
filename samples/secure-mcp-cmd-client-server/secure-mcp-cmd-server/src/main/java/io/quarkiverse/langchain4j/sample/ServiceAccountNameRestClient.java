package io.quarkiverse.langchain4j.sample;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkus.oidc.token.propagation.common.AccessToken;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;

@RegisterRestClient
@AccessToken 
public interface ServiceAccountNameRestClient {

    @GET
    @Produces("text/plain")
    String getServiceAccountName();
}

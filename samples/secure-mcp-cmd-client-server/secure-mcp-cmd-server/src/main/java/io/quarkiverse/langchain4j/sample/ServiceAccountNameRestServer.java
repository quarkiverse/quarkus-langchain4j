package io.quarkiverse.langchain4j.sample;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/service-account-name")
public class ServiceAccountNameRestServer {

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Produces("text/plain")
    @Authenticated
    public String getServiceAccountName() {
        return securityIdentity.getPrincipal().getName();
    }
}

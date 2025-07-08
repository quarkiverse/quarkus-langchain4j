package io.quarkiverse.langchain4j.sample;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/user-name-service")
public class UserNameRestServer {

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Produces("text/plain")
    @Authenticated
    public String getUserName() {
        return securityIdentity.getPrincipal().getName();
    }
}

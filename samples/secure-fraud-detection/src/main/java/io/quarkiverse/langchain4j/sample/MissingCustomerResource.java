package io.quarkiverse.langchain4j.sample;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.oidc.IdToken;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/missingCustomer")
@Authenticated
public class MissingCustomerResource {

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    Template missingCustomer;

    @GET
    @Produces("text/html")
    public TemplateInstance missingCustomer() {
        return missingCustomer.data("given_name", idToken.getClaim("given_name")).data("name", idToken.getName())
                .data("email", idToken.getClaim(Claims.email));
    }
}

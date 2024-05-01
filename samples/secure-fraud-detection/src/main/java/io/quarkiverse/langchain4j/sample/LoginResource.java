package io.quarkiverse.langchain4j.sample;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.oidc.IdToken;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 * Login resource which returns a fraud detection page to the authenticated user
 */
@Path("/login")
@Authenticated
public class LoginResource {

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    Template fraudDetection;

    @GET
    @Produces("text/html")
    public TemplateInstance fraudDetection() {
        return fraudDetection.data("name", idToken.getName());
    }
}

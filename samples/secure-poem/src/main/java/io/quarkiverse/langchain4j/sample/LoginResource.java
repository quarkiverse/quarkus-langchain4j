package io.quarkiverse.langchain4j.sample;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.oidc.IdToken;
import io.quarkus.oidc.Tenant;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 * Login resource which returns a poem welcome page to the authenticated user
 */
@Path("/login")
@Authenticated
public class LoginResource {

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    Template poem;

    @GET
    @Path("google")
    @Produces("text/html")
    @Tenant("google")
    public TemplateInstance poemGoogle() {
        return poem.data("name", idToken.getName()).data("model", "gemini").data("logout", "google");
    }

    @GET
    @Path("entraid")
    @Produces("text/html")
    @Tenant("entraid")
    public TemplateInstance poemEntraID() {
        return poem.data("name", idToken.getName()).data("model", "azureopenai").data("logout", "entraid");
    }
}

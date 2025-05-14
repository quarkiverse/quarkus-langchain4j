package io.quarkiverse.langchain4j.sample;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.oidc.UserInfo;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

/**
 * Login resource which returns a poem welcome page to the authenticated user
 */
@Path("/login")
@Authenticated
public class LoginResource {

    @Inject
    UserInfo userInfo;

    @Inject
    Template poem;

    @GET
    @Produces("text/html")
    public TemplateInstance poem() {
        return poem.data("name", userInfo.getName());
    }
}

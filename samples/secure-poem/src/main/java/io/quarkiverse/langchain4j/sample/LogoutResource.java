package io.quarkiverse.langchain4j.sample;

import io.quarkus.oidc.OidcSession;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Logout resource 
 */
@Path("/logout")
@Authenticated
public class LogoutResource {

    @Inject
    OidcSession session;

    @GET
    public Response logout(@Context UriInfo uriInfo) {
    	// remove the local session cookie
    	session.logout().await().indefinitely();
    	// redirect to the login page
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("login").build()).build();
    }
}

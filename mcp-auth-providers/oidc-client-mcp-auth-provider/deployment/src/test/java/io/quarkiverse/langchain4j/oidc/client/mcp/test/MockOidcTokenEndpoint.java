package io.quarkiverse.langchain4j.oidc.client.mcp.test;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/oidc")
public class MockOidcTokenEndpoint {

    @POST
    @Path("/provider1/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public String provider1Token(@FormParam("grant_type") String grantType) {
        return """
                {"access_token":"token-from-provider1","token_type":"Bearer","expires_in":3600}
                """;
    }

    @POST
    @Path("/provider2/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public String provider2Token(@FormParam("grant_type") String grantType) {
        return """
                {"access_token":"token-from-provider2","token_type":"Bearer","expires_in":3600}
                """;
    }
}

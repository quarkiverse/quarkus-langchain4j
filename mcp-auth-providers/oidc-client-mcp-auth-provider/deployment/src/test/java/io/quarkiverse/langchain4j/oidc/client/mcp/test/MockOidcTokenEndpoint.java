package io.quarkiverse.langchain4j.oidc.client.mcp.test;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/oidc")
@ApplicationScoped
public class MockOidcTokenEndpoint {

    private final AtomicInteger provider1TokenRequests = new AtomicInteger();
    private final AtomicInteger refreshTokenRequests = new AtomicInteger();

    @POST
    @Path("/provider1/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public String provider1Token(@FormParam("grant_type") String grantType) {
        provider1TokenRequests.incrementAndGet();
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

    @POST
    @Path("/refreshing/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public String refreshingToken(@FormParam("grant_type") String grantType) {
        boolean refresh = "refresh_token".equals(grantType);
        if (refresh) {
            refreshTokenRequests.incrementAndGet();
        }
        return """
                {"access_token":"%s","token_type":"Bearer","expires_in":1,
                 "refresh_token":"refresh-token","refresh_expires_in":3600}
                """.formatted(refresh ? "refreshed-token" : "initial-token");
    }

    public int provider1TokenRequests() {
        return provider1TokenRequests.get();
    }

    public int refreshTokenRequests() {
        return refreshTokenRequests.get();
    }
}

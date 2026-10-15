package io.quarkiverse.langchain4j.sample.wif;

import io.quarkus.spiffe.client.SpiffeClient;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Shows how to use the programmatic SPIFFE Workload API to acquire a fresh JWT-SVID.
 * <p>
 * The chat flow itself does not need this: the workload model auth provider acquires and refreshes
 * the JWT-SVID on its own. This endpoint exists so that the Workload Identity Federation setup can be
 * verified, by comparing the subject and audience of the issued token against the federation rule
 * registered with Anthropic.
 */
@Path("/api/identity")
public class WorkloadIdentityResource {

    @Inject
    SpiffeClient spiffeClient;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> identity() {
        return spiffeClient.getWorkloadJsonWebToken()
                .map(svid -> "subject: " + svid.subject()
                        + "\naudience: " + svid.audience()
                        + "\nexpiry: " + svid.expiry() + "\n");
    }
}

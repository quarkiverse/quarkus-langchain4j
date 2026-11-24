package io.quarkiverse.langchain4j.agentic.devmode;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/pricing")
public class PricingResource {

    @Inject
    PricingWorkflow workflow;

    @GET
    public String price() {
        try {
            return workflow.price("Fiat 500");
        } catch (RuntimeException e) {
            // no A2A server listens at the configured URL: the failure names the URL that the supplier returned
            StringBuilder messages = new StringBuilder();
            for (Throwable t = e; t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            return messages.toString();
        }
    }
}

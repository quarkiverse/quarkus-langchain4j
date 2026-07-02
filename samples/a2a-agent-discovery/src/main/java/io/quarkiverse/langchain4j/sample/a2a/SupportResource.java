package io.quarkiverse.langchain4j.sample.a2a;

import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;

@Path("/support")
public class SupportResource {

    @Inject
    SupportRouter.TicketClassifier classifier;

    @Inject
    ApicurioAgentsRegistry registry;

    @GET
    @Path("/agents")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> listAgents() {
        return registry.allAgents().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().description()));
    }

    @GET
    @Path("/classify")
    @Produces(MediaType.TEXT_PLAIN)
    public String classify(@RestQuery String request) {
        return classifier.classify(request);
    }

    @POST
    @Path("/ticket")
    @Produces(MediaType.APPLICATION_JSON)
    public TicketResponse handleTicket(@RestQuery String request) {
        String department = classifier.classify(request);
        String agentName = department.trim().toLowerCase() + "-agent";

        try {
            var agent = registry.getAgent(agentName);
            return new TicketResponse(department, agentName, agent.description(), "routed");
        } catch (RuntimeException e) {
            return new TicketResponse(department, agentName, null, "no agent found for department");
        }
    }

    public record TicketResponse(String department, String agentName, String agentDescription, String status) {
    }
}

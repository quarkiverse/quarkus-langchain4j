package io.quarkiverse.langchain4j.sample.a2a;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.PublishToAgentRegistry;

/**
 * Publishes this application's own agent card to the registry on startup,
 * making it discoverable by other applications.
 */
@PublishToAgentRegistry(
        name = "Support Router",
        description = "Routes customer support requests to specialized agents",
        version = "1.0.0",
        skills = {
                @PublishToAgentRegistry.Skill(
                        id = "ticket-routing",
                        name = "Ticket Routing",
                        description = "Classifies and routes support tickets to the appropriate department")
        })
@RegisterAiService
public interface AgentCardPublisherConfig {
    String route(String request);
}

package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import java.util.HashMap;
import java.util.Map;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgentsRegistry;
import io.apicurio.registry.rest.client.RegistryClient;

public class ApicurioAgentsRegistry implements AgentsRegistry {

    private static final Logger log = Logger.getLogger(ApicurioAgentsRegistry.class);
    private static final String AGENT_CARD_TYPE = "AGENT_CARD";

    private final RegistryClient registryClient;
    private volatile Map<String, AgentInstance> agents;

    public ApicurioAgentsRegistry(RegistryClient registryClient) {
        this.registryClient = registryClient;
    }

    @Override
    public Map<String, AgentInstance> allAgents() {
        if (agents == null) {
            synchronized (this) {
                if (agents == null) {
                    agents = discoverAgents();
                }
            }
        }
        return agents;
    }

    @Override
    public AgentInstance getAgent(String name) {
        AgentInstance agent = allAgents().get(name);
        if (agent == null) {
            throw new RuntimeException("No agent found with name: " + name);
        }
        return agent;
    }

    private Map<String, AgentInstance> discoverAgents() {
        Map<String, AgentInstance> discovered = new HashMap<>();

        try {
            var results = registryClient.search().artifacts().get(config -> {
                config.queryParameters.artifactType = AGENT_CARD_TYPE;
                config.queryParameters.limit = 100;
            });

            if (results == null || results.getArtifacts() == null) {
                return discovered;
            }

            for (var artifact : results.getArtifacts()) {
                try {
                    String agentUrl = extractAgentUrl(artifact);
                    if (agentUrl == null) {
                        continue;
                    }

                    String name = artifact.getName() != null ? artifact.getName() : artifact.getArtifactId();
                    AgentInstance a2aAgent = (AgentInstance) AgenticServices.a2aBuilder(agentUrl)
                            .outputKey(name)
                            .build();

                    discovered.put(name, a2aAgent);
                    log.debugf("Discovered A2A agent '%s' at %s", name, agentUrl);
                } catch (Exception e) {
                    log.debugf(e, "Failed to create A2A agent from artifact %s/%s",
                            artifact.getGroupId(), artifact.getArtifactId());
                }
            }
        } catch (Exception e) {
            log.warnf(e, "Failed to discover agents from Apicurio Registry");
        }

        return Map.copyOf(discovered);
    }

    private String extractAgentUrl(io.apicurio.registry.rest.client.models.SearchedArtifact artifact) {
        var labels = artifact.getLabels();
        if (labels == null) {
            return null;
        }
        java.util.Map<String, Object> labelData = labels.getAdditionalData();
        if (labelData == null) {
            return null;
        }
        Object url = labelData.get("a2a-agent-url");
        return url != null ? url.toString() : null;
    }
}

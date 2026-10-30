package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgentsRegistry;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SearchedArtifact;

public class ApicurioAgentsRegistry implements AgentsRegistry {

    private static final Logger log = Logger.getLogger(ApicurioAgentsRegistry.class);
    private static final String AGENT_CARD_TYPE = "AGENT_CARD";
    private static final int DEFAULT_LIMIT = 100;
    private static final String AGENT_URL_LABEL = "a2a-agent-url";
    private static final String INPUT_KEYS_LABEL = "a2a-input-keys";
    private static final List<String> FALLBACK_INPUT_KEYS = List.of("input");

    private final RegistryClient registryClient;
    private final List<String> defaultInputKeys;

    public ApicurioAgentsRegistry(RegistryClient registryClient) {
        this(registryClient, FALLBACK_INPUT_KEYS);
    }

    public ApicurioAgentsRegistry(RegistryClient registryClient, List<String> defaultInputKeys) {
        this.registryClient = registryClient;
        this.defaultInputKeys = defaultInputKeys == null || defaultInputKeys.isEmpty()
                ? FALLBACK_INPUT_KEYS
                : List.copyOf(defaultInputKeys);
    }

    @Override
    public Map<String, AgentInstance> allAgents() {
        return discoverAgents();
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
                config.queryParameters.limit = DEFAULT_LIMIT;
            });

            if (results == null || results.getArtifacts() == null) {
                return discovered;
            }

            if (results.getCount() != null && results.getCount() > DEFAULT_LIMIT) {
                log.warnf("Apicurio Registry contains %d AGENT_CARD artifacts but only %d were fetched. "
                        + "Some agents will not be discovered.", results.getCount(), DEFAULT_LIMIT);
            }

            for (var artifact : results.getArtifacts()) {
                try {
                    String agentUrl = extractAgentUrl(artifact);
                    if (agentUrl == null) {
                        continue;
                    }

                    String name = artifact.getName() != null ? artifact.getName() : artifact.getArtifactId();
                    // Agent cards carry no input schema, so discovered agents are untyped and the input keys
                    // must be supplied explicitly. They are both the arguments exposed to a supervisor and the
                    // keys read from the AgenticScope on invocation. Note that this is unrelated to the
                    // @V("input") parameter of UntypedAgent#invoke, which is the whole argument map.
                    String[] inputKeys = resolveInputKeys(artifact);

                    AgentInstance a2aAgent = (AgentInstance) AgenticServices.a2aBuilder(agentUrl)
                            .inputKeys(inputKeys)
                            .outputKey(name)
                            .build();

                    discovered.put(name, a2aAgent);
                    log.debugf("Discovered A2A agent '%s' at %s with input keys %s",
                            name, agentUrl, Arrays.toString(inputKeys));
                } catch (Exception e) {
                    log.warnf(e, "Failed to create A2A agent from artifact %s/%s",
                            artifact.getGroupId(), artifact.getArtifactId());
                }
            }
        } catch (Exception e) {
            log.warnf(e, "Failed to discover agents from Apicurio Registry");
        }

        return Map.copyOf(discovered);
    }

    // visible for testing
    String[] resolveInputKeys(SearchedArtifact artifact) {
        String declared = extractLabel(artifact, INPUT_KEYS_LABEL);
        if (declared != null && !declared.isBlank()) {
            String[] keys = Stream.of(declared.split(","))
                    .map(String::trim)
                    .filter(key -> !key.isEmpty())
                    .toArray(String[]::new);
            if (keys.length > 0) {
                return keys;
            }
            log.warnf("Artifact %s/%s declares an empty '%s' label, falling back to %s",
                    artifact.getGroupId(), artifact.getArtifactId(), INPUT_KEYS_LABEL, defaultInputKeys);
        }
        return defaultInputKeys.toArray(String[]::new);
    }

    private String extractAgentUrl(SearchedArtifact artifact) {
        return extractLabel(artifact, AGENT_URL_LABEL);
    }

    private String extractLabel(SearchedArtifact artifact, String key) {
        var labels = artifact.getLabels();
        if (labels == null) {
            return null;
        }
        Map<String, Object> labelData = labels.getAdditionalData();
        if (labelData == null) {
            return null;
        }
        Object value = labelData.get(key);
        return value != null ? value.toString() : null;
    }
}

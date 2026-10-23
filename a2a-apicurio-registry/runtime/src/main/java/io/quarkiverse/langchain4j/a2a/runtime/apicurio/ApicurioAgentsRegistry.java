package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgentsRegistry;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SearchedArtifact;

public class ApicurioAgentsRegistry implements AgentsRegistry {

    private static final Logger log = Logger.getLogger(ApicurioAgentsRegistry.class);
    private static final String AGENT_CARD_TYPE = "AGENT_CARD";
    private static final String DEFAULT_GROUP = "default";
    private static final String AGENT_URL_LABEL = "a2a-agent-url";
    private static final String INPUT_KEYS_LABEL = "a2a-input-keys";
    private static final List<String> FALLBACK_INPUT_KEYS = List.of("input");

    // Page size for each search request. Discovery pages through all matching artifacts
    // regardless of this value, so it only affects the number of round trips.
    private static final int DEFAULT_PAGE_SIZE = 100;

    // Safety net against an unbounded loop if the registry ever returns a reported count that
    // never catches up with the offset (for example a server bug that ignores the offset
    // parameter). 1000 pages is far beyond any realistic agent inventory.
    private static final int MAX_PAGES = 1000;

    // The spec's canonical transport identifier (org.a2aproject.sdk.spec.TransportProtocol#JSONRPC).
    // Matched case-insensitively since this extension's own publisher (and some other card
    // producers) write the lowercase form.
    private static final String JSONRPC_BINDING = "JSONRPC";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RegistryClient registryClient;
    private final List<String> defaultInputKeys;
    private final int pageSize;

    public ApicurioAgentsRegistry(RegistryClient registryClient) {
        this(registryClient, FALLBACK_INPUT_KEYS);
    }

    public ApicurioAgentsRegistry(RegistryClient registryClient, List<String> defaultInputKeys) {
        this(registryClient, defaultInputKeys, DEFAULT_PAGE_SIZE);
    }

    // visible for testing: lets paging tests use a small page size instead of a large fixture.
    ApicurioAgentsRegistry(RegistryClient registryClient, List<String> defaultInputKeys, int pageSize) {
        this.registryClient = registryClient;
        this.defaultInputKeys = defaultInputKeys == null || defaultInputKeys.isEmpty()
                ? FALLBACK_INPUT_KEYS
                : List.copyOf(defaultInputKeys);
        this.pageSize = pageSize;
    }

    @Override
    public Map<String, AgentInstance> allAgents() {
        return discover().agents();
    }

    @Override
    public AgentInstance getAgent(String name) {
        Discovery discovery = discover();
        AgentInstance agent = discovery.agents().get(name);
        if (agent != null) {
            return agent;
        }
        List<String> ambiguousKeys = discovery.ambiguousNames().get(name);
        if (ambiguousKeys != null) {
            throw new RuntimeException("Agent name '" + name + "' is used by " + ambiguousKeys.size()
                    + " artifacts and cannot be resolved unambiguously; request one of its qualified names "
                    + "instead: " + String.join(", ", ambiguousKeys));
        }
        throw new RuntimeException("No agent found with name: " + name);
    }

    /**
     * Discovers every {@code AGENT_CARD} artifact, paging through the full result set. Two
     * artifacts may declare the same display name; neither is silently dropped nor silently
     * overwritten. Instead, every artifact whose name collides with another is registered under
     * its {@code groupId/artifactId} key, and the colliding plain name resolves through
     * {@link #getAgent(String)} to an explicit error listing those qualified keys.
     */
    private Discovery discover() {
        // Keyed by the eventual registration key (plain name, or "groupId/artifactId" once a
        // collision is detected). Insertion order is preserved so logging and qualified-key
        // listings stay stable between calls.
        Map<String, Candidate> byQualifiedKey = new LinkedHashMap<>();
        Map<String, List<String>> qualifiedKeysByName = new LinkedHashMap<>();

        try {
            int offset = 0;
            for (int page = 0; page < MAX_PAGES; page++) {
                final int currentOffset = offset;
                var results = registryClient.search().artifacts().get(config -> {
                    config.queryParameters.artifactType = AGENT_CARD_TYPE;
                    config.queryParameters.limit = pageSize;
                    config.queryParameters.offset = currentOffset;
                });

                if (results == null || results.getArtifacts() == null || results.getArtifacts().isEmpty()) {
                    break;
                }

                for (var artifact : results.getArtifacts()) {
                    try {
                        String agentUrl = resolveAgentUrl(artifact);
                        if (agentUrl == null) {
                            continue;
                        }

                        String name = artifact.getName() != null ? artifact.getName() : artifact.getArtifactId();
                        String qualifiedKey = qualifiedKey(artifact);
                        // Agent cards carry no input schema, so discovered agents are untyped and the input
                        // keys must be supplied explicitly. They are both the arguments exposed to a
                        // supervisor and the keys read from the AgenticScope on invocation. Note that this
                        // is unrelated to the @V("input") parameter of UntypedAgent#invoke, which is the
                        // whole argument map.
                        String[] inputKeys = resolveInputKeys(artifact);

                        byQualifiedKey.put(qualifiedKey, new Candidate(name, agentUrl, inputKeys));
                        qualifiedKeysByName.computeIfAbsent(name, k -> new ArrayList<>()).add(qualifiedKey);
                    } catch (Exception e) {
                        log.warnf(e, "Failed to resolve A2A agent from artifact %s/%s",
                                artifact.getGroupId(), artifact.getArtifactId());
                    }
                }

                int fetched = results.getArtifacts().size();
                offset += fetched;
                Integer total = results.getCount();
                if (total != null && offset >= total) {
                    break;
                }
                if (fetched < pageSize) {
                    // A short page means there is nothing more to fetch, even if the reported
                    // count is missing or does not match what was actually returned.
                    break;
                }
            }
        } catch (Exception e) {
            log.warnf(e, "Failed to discover agents from Apicurio Registry");
            return new Discovery(Map.of(), Map.of());
        }

        return resolve(byQualifiedKey, qualifiedKeysByName);
    }

    private Discovery resolve(Map<String, Candidate> byQualifiedKey, Map<String, List<String>> qualifiedKeysByName) {
        Map<String, AgentInstance> discovered = new HashMap<>();
        Map<String, List<String>> ambiguousNames = new HashMap<>();

        for (var entry : qualifiedKeysByName.entrySet()) {
            String name = entry.getKey();
            List<String> qualifiedKeys = entry.getValue();

            if (qualifiedKeys.size() == 1) {
                registerAgent(discovered, name, byQualifiedKey.get(qualifiedKeys.get(0)));
                continue;
            }

            log.warnf("Agent name '%s' is used by %d artifacts (%s); registering each under its "
                    + "groupId/artifactId key instead of the ambiguous name", name, qualifiedKeys.size(),
                    String.join(", ", qualifiedKeys));
            for (String qualifiedKey : qualifiedKeys) {
                registerAgent(discovered, qualifiedKey, byQualifiedKey.get(qualifiedKey));
            }
            ambiguousNames.put(name, List.copyOf(qualifiedKeys));
        }

        return new Discovery(Map.copyOf(discovered), Map.copyOf(ambiguousNames));
    }

    private void registerAgent(Map<String, AgentInstance> discovered, String registrationKey, Candidate candidate) {
        try {
            AgentInstance agent = (AgentInstance) AgenticServices.a2aBuilder(candidate.agentUrl())
                    .inputKeys(candidate.inputKeys())
                    .outputKey(registrationKey)
                    .build();
            discovered.put(registrationKey, agent);
            log.debugf("Discovered A2A agent '%s' at %s with input keys %s",
                    registrationKey, candidate.agentUrl(), Arrays.toString(candidate.inputKeys()));
        } catch (Exception e) {
            log.warnf(e, "Failed to build A2A agent '%s' at %s", registrationKey, candidate.agentUrl());
        }
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

    /**
     * Resolves the agent's URL. The {@code a2a-agent-url} label is the fast path used by this
     * extension's own publisher. When it is absent, the agent's card content is read and its
     * {@code supportedInterfaces} are searched for a JSONRPC entry, so agents published by other
     * means (or future cards that don't need the label at all) remain discoverable. Returns
     * {@code null}, logging why, when neither resolves to a usable URL.
     */
    private String resolveAgentUrl(SearchedArtifact artifact) {
        String labeled = extractLabel(artifact, AGENT_URL_LABEL);
        if (labeled != null && !labeled.isBlank()) {
            return labeled;
        }
        return resolveAgentUrlFromCard(artifact);
    }

    private String resolveAgentUrlFromCard(SearchedArtifact artifact) {
        String groupId = artifact.getGroupId() != null ? artifact.getGroupId() : DEFAULT_GROUP;
        String artifactId = artifact.getArtifactId();

        try (InputStream content = registryClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get()) {
            JsonNode card = OBJECT_MAPPER.readTree(content);
            JsonNode interfaces = card.get("supportedInterfaces");
            if (interfaces == null || !interfaces.isArray()) {
                log.warnf("Artifact %s/%s has no '%s' label and its card declares no supportedInterfaces; "
                        + "skipping", groupId, artifactId, AGENT_URL_LABEL);
                return null;
            }
            for (JsonNode candidateInterface : interfaces) {
                JsonNode binding = candidateInterface.get("protocolBinding");
                JsonNode url = candidateInterface.get("url");
                if (binding != null && url != null && !url.asText().isBlank()
                        && JSONRPC_BINDING.equalsIgnoreCase(binding.asText())) {
                    return url.asText();
                }
            }
            log.warnf("Artifact %s/%s has no '%s' label and its card declares no JSONRPC interface; skipping",
                    groupId, artifactId, AGENT_URL_LABEL);
            return null;
        } catch (Exception e) {
            log.warnf(e, "Artifact %s/%s has no '%s' label and its card content could not be read or parsed; "
                    + "skipping", groupId, artifactId, AGENT_URL_LABEL);
            return null;
        }
    }

    private String qualifiedKey(SearchedArtifact artifact) {
        String groupId = artifact.getGroupId() != null ? artifact.getGroupId() : DEFAULT_GROUP;
        return groupId + "/" + artifact.getArtifactId();
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

    private record Candidate(String name, String agentUrl, String[] inputKeys) {
    }

    private record Discovery(Map<String, AgentInstance> agents, Map<String, List<String>> ambiguousNames) {
    }
}

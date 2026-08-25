package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import java.util.List;
import java.util.stream.Collectors;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.VersionContent;

public class A2AAgentCardPublisher {

    private static final Logger log = Logger.getLogger(A2AAgentCardPublisher.class);
    private static final String AGENT_CARD_TYPE = "AGENT_CARD";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RegistryClient registryClient;
    private final String groupId;
    private final String agentName;
    private final String agentDescription;
    private final String agentUrl;
    private final String agentVersion;
    private final List<SkillInfo> skills;

    public A2AAgentCardPublisher(RegistryClient registryClient, String groupId,
            String agentName, String agentDescription, String agentUrl,
            String agentVersion, List<SkillInfo> skills) {
        this.registryClient = registryClient;
        this.groupId = groupId;
        this.agentName = agentName;
        this.agentDescription = agentDescription;
        this.agentUrl = agentUrl;
        this.agentVersion = agentVersion;
        this.skills = skills;
    }

    public void publish() {
        AgentCard agentCard = buildAgentCard();
        String agentCardJson = serializeAgentCard(agentCard);
        CreateArtifact createArtifact = buildCreateArtifact(agentCardJson);

        try {
            registryClient.groups().byGroupId(groupId).artifacts().post(createArtifact);
        } catch (Exception e) {
            log.errorf(e, "Failed to publish agent card '%s' to Apicurio Registry", agentName);
            return;
        }

        String artifactId = sanitizeId(agentName);
        log.infof("Published agent card '%s' to Apicurio Registry (group=%s, artifact=%s)",
                agentName, groupId, artifactId);
    }

    private String serializeAgentCard(AgentCard agentCard) {
        try {
            return OBJECT_MAPPER.writeValueAsString(agentCard);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent card for '" + agentName + "'", e);
        }
    }

    private CreateArtifact buildCreateArtifact(String agentCardJson) {
        String artifactId = sanitizeId(agentName);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(AGENT_CARD_TYPE);
        createArtifact.setName(agentName);
        createArtifact.setDescription(agentDescription);

        Labels artifactLabels = new Labels();
        artifactLabels.getAdditionalData().put("a2a-agent-url", agentUrl);
        artifactLabels.getAdditionalData().put("a2a-agent-skills",
                skills.stream().map(SkillInfo::name).collect(Collectors.joining(", ")));
        createArtifact.setLabels(artifactLabels);

        VersionContent content = new VersionContent();
        content.setContent(agentCardJson);
        content.setContentType("application/json");

        CreateVersion firstVersion = new CreateVersion();
        firstVersion.setVersion(agentVersion);
        firstVersion.setContent(content);
        createArtifact.setFirstVersion(firstVersion);

        return createArtifact;
    }

    private AgentCard buildAgentCard() {
        List<AgentSkill> agentSkills = skills.stream()
                .map(s -> AgentSkill.builder()
                        .id(s.id())
                        .name(s.name())
                        .description(s.description())
                        .tags(List.of())
                        .examples(List.of())
                        .inputModes(List.of("text"))
                        .outputModes(List.of("text"))
                        .build())
                .toList();

        AgentCapabilities capabilities = AgentCapabilities.builder()
                .streaming(false)
                .pushNotifications(false)
                .build();

        AgentInterface jsonrpcInterface = new AgentInterface("jsonrpc", agentUrl);

        return AgentCard.builder()
                .name(agentName)
                .description(agentDescription)
                .url(agentUrl)
                .version(agentVersion)
                .skills(agentSkills)
                .capabilities(capabilities)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .supportedInterfaces(List.of(jsonrpcInterface))
                .build();
    }

    private static String sanitizeId(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    public record SkillInfo(String id, String name, String description) {
    }
}

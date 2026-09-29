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
import com.microsoft.kiota.ApiException;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;

public class A2AAgentCardPublisher {

    private static final Logger log = Logger.getLogger(A2AAgentCardPublisher.class);
    private static final String VERSION_ALREADY_EXISTS = "VersionAlreadyExistsException";
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
        String artifactId = sanitizeId(agentName);
        CreateArtifact createArtifact = buildCreateArtifact(artifactId, agentCardJson);

        try {
            try {
                registryClient.groups().byGroupId(groupId).artifacts().post(createArtifact,
                        config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);
            } catch (ApiException e) {
                if (!VERSION_ALREADY_EXISTS.equals(problemName(e))) {
                    throw e;
                }
                // card changed but the agent version didn't, let the registry assign one
                log.debugf("Version '%s' of agent card '%s' already exists, creating a new version",
                        agentVersion, agentName);
                CreateVersion newVersion = new CreateVersion();
                newVersion.setContent(buildVersionContent(agentCardJson));
                registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                        .post(newVersion);
            }

            // labels are not updated when a new version is added
            EditableArtifactMetaData metaData = new EditableArtifactMetaData();
            metaData.setName(agentName);
            metaData.setDescription(agentDescription);
            metaData.setLabels(buildLabels());
            registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(metaData);
        } catch (Exception e) {
            String detail = problemDetail(e);
            if (detail != null) {
                log.errorf(e, "Failed to publish agent card '%s' to Apicurio Registry: %s", agentName, detail);
            } else {
                log.errorf(e, "Failed to publish agent card '%s' to Apicurio Registry", agentName);
            }
            return;
        }

        log.infof("Published agent card '%s' to Apicurio Registry (group=%s, artifact=%s)",
                agentName, groupId, artifactId);
    }

    // rule violations (400) are mapped to RuleViolationProblemDetails, which doesn't extend ProblemDetails
    private static String problemName(Exception e) {
        if (e instanceof ProblemDetails pd) {
            return pd.getName();
        }
        if (e instanceof RuleViolationProblemDetails rv) {
            return rv.getName();
        }
        return null;
    }

    private static String problemDetail(Exception e) {
        if (e instanceof ProblemDetails pd) {
            return pd.getName() + " (status=" + pd.getStatus() + "): " + pd.getDetail();
        }
        if (e instanceof RuleViolationProblemDetails rv) {
            return rv.getName() + " (status=" + rv.getStatus() + "): " + rv.getDetail();
        }
        return null;
    }

    private String serializeAgentCard(AgentCard agentCard) {
        try {
            return OBJECT_MAPPER.writeValueAsString(agentCard);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent card for '" + agentName + "'", e);
        }
    }

    private CreateArtifact buildCreateArtifact(String artifactId, String agentCardJson) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(AGENT_CARD_TYPE);
        createArtifact.setName(agentName);
        createArtifact.setDescription(agentDescription);
        createArtifact.setLabels(buildLabels());

        CreateVersion firstVersion = new CreateVersion();
        firstVersion.setVersion(agentVersion);
        firstVersion.setContent(buildVersionContent(agentCardJson));
        createArtifact.setFirstVersion(firstVersion);

        return createArtifact;
    }

    private Labels buildLabels() {
        Labels labels = new Labels();
        labels.getAdditionalData().put("a2a-agent-url", agentUrl);
        labels.getAdditionalData().put("a2a-agent-skills",
                skills.stream().map(SkillInfo::name).collect(Collectors.joining(", ")));
        return labels;
    }

    private static VersionContent buildVersionContent(String agentCardJson) {
        VersionContent content = new VersionContent();
        content.setContent(agentCardJson);
        content.setContentType("application/json");
        return content;
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

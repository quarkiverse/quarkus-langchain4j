package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkus.test.QuarkusUnitTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApicurioRegistryA2AToolsTest {

    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:latest-snapshot";
    private static final String REGISTRY_URL_PROPERTY = "test.apicurio.registry.url";

    @SuppressWarnings("resource")
    static GenericContainer<?> registryContainer;

    static String registryUrl() {
        String existing = System.getProperty(REGISTRY_URL_PROPERTY);
        if (existing != null) {
            return existing;
        }
        registryContainer = new GenericContainer<>(REGISTRY_IMAGE)
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/registry/v3/system/info")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        registryContainer.start();
        String url = "http://" + registryContainer.getHost() + ":"
                + registryContainer.getMappedPort(8080) + "/apis/registry/v3";
        System.setProperty(REGISTRY_URL_PROPERTY, url);
        return url;
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.http.test-port=0\n"
                                            + "quarkus.langchain4j.a2a.apicurio-registry.url=" + registryUrl()
                                            + "\n"),
                            "application.properties"));

    @Inject
    ApicurioAgentsRegistry agentsRegistry;

    @BeforeAll
    static void registerAgentCards() {
        RegistryClientOptions options = RegistryClientOptions.create(registryUrl());
        RegistryClient client = RegistryClientFactory.create(options);

        registerAgentCard(client, "translation-agent", "Translation Agent",
                "Translates text between languages",
                "http://localhost:9999/a2a");

        registerAgentCard(client, "sentiment-agent", "Sentiment Analysis Agent",
                "Analyzes sentiment of provided text",
                "http://localhost:9998/a2a");
    }

    private static void registerAgentCard(RegistryClient client, String artifactId, String name,
            String description, String agentUrl) {
        String agentCardJson = String.format("""
                {
                    "name": "%s",
                    "description": "%s",
                    "url": "%s",
                    "version": "1.0.0",
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"],
                    "capabilities": {
                        "streaming": false,
                        "pushNotifications": false,
                        "stateTransitionHistory": false
                    }
                }
                """, name, description, agentUrl);

        Labels labels = new Labels();
        HashMap<String, Object> labelData = new HashMap<>();
        labelData.put("a2a-agent-url", agentUrl);
        labels.setAdditionalData(labelData);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType("AGENT_CARD");
        createArtifact.setName(name);
        createArtifact.setDescription(description);
        createArtifact.setLabels(labels);

        CreateVersion firstVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(agentCardJson);
        content.setContentType("application/json");
        firstVersion.setContent(content);
        createArtifact.setFirstVersion(firstVersion);

        try {
            client.groups().byGroupId("default").artifacts().post(createArtifact);
        } catch (ProblemDetails e) {
            throw new RuntimeException("Failed to register AGENT_CARD artifact (detail: "
                    + e.getDetail() + ", title: " + e.getTitle() + ")", e);
        }
    }

    @AfterAll
    static void stopContainer() {
        System.clearProperty(REGISTRY_URL_PROPERTY);
        if (registryContainer != null) {
            registryContainer.stop();
        }
    }

    @Test
    @Order(1)
    void beanIsInjected() {
        assertThat(agentsRegistry).isNotNull();
    }

    @Test
    @Order(2)
    void discoverAgentsFindsRegisteredCards() {
        var agents = agentsRegistry.allAgents().values();
        assertThat(agents).isNotEmpty();
    }

    @Test
    @Order(3)
    void discoveredAgentsHaveCorrectMetadata() {
        var agents = agentsRegistry.allAgents().values();
        var agentIds = agents.stream().map(a -> a.name()).toList();
        assertThat(agentIds).contains("Translation Agent", "Sentiment Analysis Agent");
    }
}

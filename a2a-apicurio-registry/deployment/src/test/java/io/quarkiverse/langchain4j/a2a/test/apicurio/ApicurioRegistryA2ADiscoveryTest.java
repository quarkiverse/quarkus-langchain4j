package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.A2AAgentCardPublisher;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

/**
 * Discovery against a real Registry with agents that are actually reachable: a local HTTP server
 * serves a valid Agent Card at /.well-known/agent-card.json, so discovered agents can be built.
 */
public class ApicurioRegistryA2ADiscoveryTest {

    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:latest-snapshot";
    private static final String REGISTRY_URL_PROPERTY = "test.apicurio.registry.url";
    private static final int PAGED_AGENTS = 120;

    @SuppressWarnings("resource")
    static GenericContainer<?> registryContainer;
    static Vertx vertx;
    static HttpServer agentServer;
    static String agentUrl;

    /**
     * Not a configuration option. QuarkusUnitTest loads this class twice, once in the JUnit class
     * loader (which builds the archive) and once in the Quarkus class loader (which runs the
     * test), so static fields are not shared. The system property hands the URL of the one
     * container over to the second copy, which would otherwise start another registry.
     */
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
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.url", registryUrl());

    @Inject
    ApicurioAgentsRegistry agentsRegistry;

    @BeforeAll
    static void setup() throws Exception {
        vertx = Vertx.vertx();
        String[] card = new String[1];
        agentServer = vertx.createHttpServer()
                .requestHandler(request -> {
                    if (request.path().equals("/.well-known/agent-card.json")) {
                        request.response().putHeader("Content-Type", "application/json").end(card[0]);
                    } else {
                        request.response().setStatusCode(404).end();
                    }
                })
                .listen(0, "127.0.0.1")
                .toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
        agentUrl = "http://127.0.0.1:" + agentServer.actualPort();
        card[0] = agentCard("Served Agent", agentUrl);

        RegistryClient client = client();
        for (int i = 0; i < PAGED_AGENTS; i++) {
            registerAgentCard(client, "paged", "agent-" + i, "Paged Agent " + i,
                    agentCard("Paged Agent " + i, agentUrl), Map.of("a2a-agent-url", agentUrl));
        }
        registerAgentCard(client, "dup-a", "shared", "Shared Name", agentCard("Shared Name", agentUrl),
                Map.of("a2a-agent-url", agentUrl));
        registerAgentCard(client, "dup-b", "shared", "Shared Name", agentCard("Shared Name", agentUrl),
                Map.of("a2a-agent-url", agentUrl));
        // No a2a-agent-url label: the URL must come from the card's supportedInterfaces.
        registerAgentCard(client, "card-only", "from-card", "Card Only Agent",
                agentCard("Card Only Agent", agentUrl), Map.of());
        // No label and no JSONRPC interface: skipped, discovery still returns the others.
        registerAgentCard(client, "card-only", "grpc-only", "Grpc Only Agent",
                agentCard("Grpc Only Agent", agentUrl).replace("\"JSONRPC\"", "\"GRPC\""), Map.of());
        // No label and a card without supportedInterfaces: skipped.
        registerAgentCard(client, "card-only", "no-interfaces", "No Interfaces Agent",
                "{\"name\": \"No Interfaces Agent\"}", Map.of());
    }

    @AfterAll
    static void cleanup() {
        System.clearProperty(REGISTRY_URL_PROPERTY);
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
        if (registryContainer != null) {
            registryContainer.stop();
        }
    }

    @Test
    void discoversEveryAgentAcrossPagesAndResolvesCollisionsAndCardInterfaces() {
        // Assert on the keys only: discovered agents are proxies whose toString() invokes the agent.
        Set<String> names = Set.copyOf(agentsRegistry.allAgents().keySet());

        assertThat(names).containsAll(IntStream.range(0, PAGED_AGENTS)
                .mapToObj(i -> "Paged Agent " + i).toList());
        assertThat(names).contains("dup-a/shared", "dup-b/shared", "Card Only Agent");
        assertThat(names).doesNotContain("Shared Name", "Grpc Only Agent", "No Interfaces Agent");
        assertThat(names.stream().filter(n -> n.startsWith("Paged Agent "))).hasSize(PAGED_AGENTS);

        assertThatThrownBy(() -> agentsRegistry.getAgent("Shared Name"))
                .hasMessageContaining("dup-a/shared").hasMessageContaining("dup-b/shared");
    }

    @Test
    void rejectedPublicationKeepsTheAcceptedVersion() {
        RegistryClient client = client();
        String group = "rejected-publication";
        List<A2AAgentCardPublisher.SkillInfo> skills = List.of(
                new A2AAgentCardPublisher.SkillInfo("translate", "translate", "Translates text"),
                new A2AAgentCardPublisher.SkillInfo("detect", "detect", "Detects the language"));
        new A2AAgentCardPublisher(client, group, "translator", "Translates", agentUrl, "1.0.0", skills).publish();

        CreateRule rule = new CreateRule();
        rule.setRuleType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        client.groups().byGroupId(group).artifacts().byArtifactId("translator").rules().post(rule);
        var accepted = client.groups().byGroupId(group).artifacts().byArtifactId("translator").versions()
                .byVersionExpression("branch=latest").get();

        // Removing a skill is incompatible: publication is rejected (best-effort, logged) and
        // must not change the registered card.
        new A2AAgentCardPublisher(client, group, "translator", "Translates", agentUrl, "1.0.0",
                List.of(skills.get(1))).publish();

        var latest = client.groups().byGroupId(group).artifacts().byArtifactId("translator").versions()
                .byVersionExpression("branch=latest").get();
        assertThat(latest.getVersion()).isEqualTo(accepted.getVersion());
        assertThat(latest.getGlobalId()).isEqualTo(accepted.getGlobalId());
    }

    private static RegistryClient client() {
        return RegistryClientFactory.create(
                RegistryClientOptions.create(registryUrl()).httpAdapter(HttpAdapterType.JDK));
    }

    private static String agentCard(String name, String url) {
        return """
                {
                  "name": "%s",
                  "description": "A test agent",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "%s", "protocolBinding": "JSONRPC", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {},
                  "skills": [ { "id": "s", "name": "S", "description": "A skill", "tags": ["t"] } ],
                  "defaultInputModes": ["text"],
                  "defaultOutputModes": ["text"]
                }
                """.formatted(name, url);
    }

    private static void registerAgentCard(RegistryClient client, String groupId, String artifactId, String name,
            String card, Map<String, Object> labelData) {
        CreateArtifact artifact = new CreateArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setArtifactType("AGENT_CARD");
        artifact.setName(name);
        if (!labelData.isEmpty()) {
            Labels labels = new Labels();
            labels.setAdditionalData(new HashMap<>(labelData));
            artifact.setLabels(labels);
        }
        VersionContent content = new VersionContent();
        content.setContent(card);
        content.setContentType("application/json");
        CreateVersion version = new CreateVersion();
        version.setContent(content);
        artifact.setFirstVersion(version);
        postWithRetry(client, groupId, artifact);
    }

    /**
     * The registry SDK runs on the JDK HttpClient, which reuses keep-alive connections. Under the
     * volume of registrations this test performs, the server occasionally closes an idle connection
     * that the client then reuses, so the next POST fails mid-read with an {@link IOException}
     * wrapped in a {@code RuntimeException} ("could not close the reader"). Retry a few times; if a
     * retried create finds the artifact already there (the dropped request had in fact reached the
     * server), treat that {@code 409} as success.
     */
    private static void postWithRetry(RegistryClient client, String groupId, CreateArtifact artifact) {
        for (int attempt = 1;; attempt++) {
            try {
                client.groups().byGroupId(groupId).artifacts().post(artifact);
                return;
            } catch (ProblemDetails e) {
                if (Integer.valueOf(409).equals(e.getStatus())) {
                    return;
                }
                throw e;
            } catch (RuntimeException e) {
                if (attempt >= 3 || !hasIOCause(e)) {
                    throw e;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private static boolean hasIOCause(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}

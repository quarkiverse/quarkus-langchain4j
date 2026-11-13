package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import dev.langchain4j.agentic.UntypedAgent;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.A2AAgentAuthProvider;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Requests to a discovered A2A agent carry the Authorization header from the application's
 * {@link A2AAgentAuthProvider}, and the provider learns which agent the request is for.
 */
public class ApicurioRegistryA2AAgentAuthTest {

    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:latest-snapshot";
    private static final String REGISTRY_URL_PROPERTY = "test.apicurio.registry.agent-auth.url";
    private static final String GROUP = "agent-auth";

    @SuppressWarnings("resource")
    static GenericContainer<?> registryContainer;
    static Vertx vertx;
    static HttpServer agentServer;
    /** The Authorization header of each request the agent received. */
    static final List<String> receivedAuthorization = new CopyOnWriteArrayList<>();

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
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(TokenPerAgent.class))
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.url", registryUrl())
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.group-id", GROUP);

    /** Issues a token naming the agent, so the test can see which agent each request was for. */
    @ApplicationScoped
    public static class TokenPerAgent implements A2AAgentAuthProvider {
        @Override
        public String getAuthorization(DiscoveredAgent agent) {
            return "Bearer token-for-" + agent.groupId() + "/" + agent.artifactId();
        }
    }

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
                        receivedAuthorization.add(String.valueOf(request.getHeader("Authorization")));
                        request.body().onSuccess(body -> {
                            String id = new JsonObject(body).getString("id");
                            request.response().putHeader("Content-Type", "application/json").end(new JsonObject()
                                    .put("jsonrpc", "2.0").put("id", id)
                                    .put("result", new JsonObject().put("message", new JsonObject()
                                            .put("messageId", "reply").put("role", "ROLE_AGENT")
                                            .put("parts", new JsonArray().add(new JsonObject().put("text", "pong")))))
                                    .encode());
                        });
                    }
                })
                .listen(0, "127.0.0.1")
                .toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
        String agentUrl = "http://127.0.0.1:" + agentServer.actualPort();
        card[0] = """
                {
                  "name": "Secured Agent",
                  "description": "An agent that requires a token",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "%s", "protocolBinding": "JSONRPC", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {},
                  "skills": [ { "id": "s", "name": "S", "description": "A skill", "tags": ["t"] } ],
                  "defaultInputModes": ["text"],
                  "defaultOutputModes": ["text"]
                }
                """.formatted(agentUrl);

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryUrl()).httpAdapter(HttpAdapterType.JDK));
        CreateArtifact artifact = new CreateArtifact();
        artifact.setArtifactId("secured-agent");
        artifact.setArtifactType("AGENT_CARD");
        artifact.setName("Secured Agent");
        CreateVersion version = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(card[0]);
        content.setContentType("application/json");
        version.setContent(content);
        artifact.setFirstVersion(version);
        client.groups().byGroupId(GROUP).artifacts().post(artifact);
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
    void theDiscoveredAgentReceivesTheProvidersToken() {
        UntypedAgent agent = (UntypedAgent) agentsRegistry.getAgent("Secured Agent");

        assertThat(agent.invoke(Map.of("input", "hello"))).isEqualTo("pong");
        assertThat(receivedAuthorization).containsExactly("Bearer token-for-" + GROUP + "/secured-agent");
    }
}

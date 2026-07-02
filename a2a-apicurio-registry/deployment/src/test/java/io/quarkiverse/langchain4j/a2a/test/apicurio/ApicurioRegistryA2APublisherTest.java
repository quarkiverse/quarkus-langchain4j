package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.A2AAgentCardPublisher;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.PublishToAgentRegistry;
import io.quarkus.test.QuarkusUnitTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApicurioRegistryA2APublisherTest {

    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:latest-snapshot";
    private static final String REGISTRY_URL_PROPERTY = "test.apicurio.registry.publisher.url";

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
                    .addClasses(MyPublishedAgent.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.http.test-port=0\n"
                                            + "quarkus.langchain4j.a2a.apicurio-registry.url=" + registryUrl() + "\n"
                                            + "quarkus.langchain4j.a2a.apicurio-registry.agent-url=http://localhost:8080/a2a\n"
                                            + "quarkus.langchain4j.openai.api-key=dummy\n"
                                            + "quarkus.langchain4j.openai.base-url=http://localhost:0/v1\n"),
                            "application.properties"));

    @Inject
    A2AAgentCardPublisher publisher;

    @Inject
    ApicurioAgentsRegistry agentsRegistry;

    @AfterAll
    static void stopContainer() {
        System.clearProperty(REGISTRY_URL_PROPERTY);
        if (registryContainer != null) {
            registryContainer.stop();
        }
    }

    @Test
    @Order(1)
    void publisherBeanIsCreatedAndPublishes() {
        publisher.publish();
        assertThat(publisher).isNotNull();
    }

    @Test
    @Order(2)
    void publishedAgentCardIsDiscoverableViaRegistry() {
        var agents = agentsRegistry.allAgents().values();
        assertThat(agents).isNotEmpty();
        var names = agents.stream().map(a -> a.name()).toList();
        assertThat(names).contains("My Test Agent");
    }

    @PublishToAgentRegistry(name = "My Test Agent", description = "A test agent for unit testing", version = "2.0.0", skills = {
            @PublishToAgentRegistry.Skill(id = "greet", name = "Greeting", description = "Greets the user")
    })
    @RegisterAiService
    public interface MyPublishedAgent {
        String greet(String name);
    }
}

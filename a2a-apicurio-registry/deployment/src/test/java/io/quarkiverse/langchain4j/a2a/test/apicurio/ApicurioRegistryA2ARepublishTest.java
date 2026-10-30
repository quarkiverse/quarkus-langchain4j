package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.A2AAgentCardPublisher;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.A2AAgentCardPublisher.SkillInfo;

public class ApicurioRegistryA2ARepublishTest {

    private static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:latest-snapshot";
    private static final String GROUP = "default";
    private static final String ARTIFACT_ID = "republished-agent";

    static GenericContainer<?> registryContainer;
    static RegistryClient client;

    @BeforeAll
    static void startRegistry() {
        registryContainer = new GenericContainer<>(REGISTRY_IMAGE)
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/registry/v3/system/info")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        registryContainer.start();
        String url = "http://" + registryContainer.getHost() + ":"
                + registryContainer.getMappedPort(8080) + "/apis/registry/v3";
        client = RegistryClientFactory.create(RegistryClientOptions.create(url));
    }

    @AfterAll
    static void stopRegistry() {
        if (registryContainer != null) {
            registryContainer.stop();
        }
    }

    @Test
    void republishUpdatesCardAndLabels() {
        List<SkillInfo> initialSkills = List.of(new SkillInfo("greet", "Greeting", "Greets the user"));
        List<SkillInfo> changedSkills = List.of(new SkillInfo("translate", "Translation", "Translates text"));

        publisher("http://localhost:8080/a2a", "1.0.0", initialSkills).publish();
        // plain restart: identical card, must not create a new version
        publisher("http://localhost:8080/a2a", "1.0.0", initialSkills).publish();
        assertThat(versions()).extracting(SearchedVersion::getVersion).containsExactly("1.0.0");

        // restart with changed skills and URL but the same agent version
        publisher("http://localhost:9090/a2a", "1.0.0", changedSkills).publish();
        assertThat(versions()).hasSize(2);
        assertThat(latestContent()).contains("Translation").doesNotContain("Greeting");
        assertThat(labels())
                .containsEntry("a2a-agent-url", "http://localhost:9090/a2a")
                .containsEntry("a2a-agent-skills", "Translation");

        // new agent version
        publisher("http://localhost:9090/a2a", "2.0.0", initialSkills).publish();
        assertThat(versions()).extracting(SearchedVersion::getVersion).contains("2.0.0");
        assertThat(labels()).containsEntry("a2a-agent-skills", "Greeting");
    }

    private static A2AAgentCardPublisher publisher(String url, String version, List<SkillInfo> skills) {
        return new A2AAgentCardPublisher(client, GROUP, "Republished Agent", "An agent", url, version, skills);
    }

    private static List<SearchedVersion> versions() {
        return client.groups().byGroupId(GROUP).artifacts().byArtifactId(ARTIFACT_ID).versions().get(c -> {
        }).getVersions();
    }

    private static Map<String, Object> labels() {
        return client.groups().byGroupId(GROUP).artifacts().byArtifactId(ARTIFACT_ID).get().getLabels()
                .getAdditionalData();
    }

    private static String latestContent() {
        try (InputStream in = client.groups().byGroupId(GROUP).artifacts().byArtifactId(ARTIFACT_ID).versions()
                .byVersionExpression("branch=latest").content().get()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

package io.quarkiverse.langchain4j.prompt.test.apicurio;

import java.time.Duration;
import java.util.List;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;

/**
 * Starts an Apicurio Registry container (once per JVM, removed by Testcontainers when the JVM exits) and offers helpers
 * to publish PROMPT_TEMPLATE artifacts. Each test class uses its own group so that classes do not interfere.
 */
final class ApicurioRegistryTestSupport {

    static final String REGISTRY_IMAGE = "quay.io/apicurio/apicurio-registry:3.3.0";
    static final String REGISTRY_URL_PROPERTY = "test.apicurio.registry.url";
    static final String PROMPT_TEMPLATE_TYPE = "PROMPT_TEMPLATE";

    @SuppressWarnings("resource")
    private static GenericContainer<?> registryContainer;

    private ApicurioRegistryTestSupport() {
    }

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

    static RegistryClient client() {
        RegistryClientOptions options = RegistryClientOptions.create(registryUrl()).httpAdapter(HttpAdapterType.JDK);
        return RegistryClientFactory.create(options);
    }

    static void createPromptTemplate(String groupId, String artifactId, String content, String contentType) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(PROMPT_TEMPLATE_TYPE);
        createArtifact.setFirstVersion(version(content, contentType));
        client().groups().byGroupId(groupId).artifacts().post(createArtifact,
                config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);
    }

    static void createPromptTemplateVersion(String groupId, String artifactId, String content, String contentType) {
        client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(version(content, contentType));
    }

    private static CreateVersion version(String content, String contentType) {
        CreateVersion version = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(contentType);
        version.setContent(versionContent);
        return version;
    }

    /**
     * A chat model that echoes the system and user messages it receives, so tests can assert on the rendered prompts.
     */
    static class EchoChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            StringBuilder sb = new StringBuilder();
            for (ChatMessage message : chatRequest.messages()) {
                if (message instanceof SystemMessage sm) {
                    sb.append("[system]").append(sm.text());
                } else if (message instanceof UserMessage um) {
                    sb.append("[user]").append(um.singleText());
                }
            }
            return ChatResponse.builder().aiMessage(new AiMessage(sb.toString())).build();
        }

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            return doChat(ChatRequest.builder().messages(messages).build());
        }
    }
}

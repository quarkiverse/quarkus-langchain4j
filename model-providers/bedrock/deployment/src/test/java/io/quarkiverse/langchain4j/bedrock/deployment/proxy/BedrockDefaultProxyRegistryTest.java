package io.quarkiverse.langchain4j.bedrock.deployment.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.bedrock.deployment.TestCredentialsProvider;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

class BedrockDefaultProxyRegistryTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestCredentialsProvider.class, ForwardProxy.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.proxy.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.port", String.valueOf(ForwardProxy.port()));

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @ConfigProperty(name = "quarkus.langchain4j.mock-api.url")
    String mockUrl;

    @BeforeEach
    void setup() {
        mock.reset();
    }

    @AfterAll
    static void stopProxy() {
        ForwardProxy.stop();
    }

    @Test
    void should_route_request_through_default_proxy() {
        mock.chat().reply(ChatReply.text("Routed through proxy").usage(5, 3));

        var response = chatModel.chat("Hello");

        assertThat(response).isEqualTo("Routed through proxy");
        // the mock is always the endpoint, so only the proxy's record shows the request went through it
        assertThat(ForwardProxy.forwardedUris())
                .containsExactly(mockUrl + "/bedrock/model/amazon.titan-text-express-v1/converse");
        assertThat(mock.chat().singleRequest().asChat().messages()).singleElement()
                .satisfies(m -> assertThat(m.text()).isEqualTo("Hello"));
    }
}

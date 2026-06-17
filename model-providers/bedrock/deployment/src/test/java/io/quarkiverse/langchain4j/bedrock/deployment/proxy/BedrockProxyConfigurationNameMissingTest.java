package io.quarkiverse.langchain4j.bedrock.deployment.proxy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.bedrock.deployment.TestCredentialsProvider;
import io.quarkus.test.QuarkusUnitTest;

class BedrockProxyConfigurationNameMissingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.client.proxy-configuration-name", "missing");

    @Inject
    ChatModel chatModel;

    @Test
    void shouldFailWhenNamedProxyIsMissing() {
        assertThatThrownBy(() -> chatModel.chat("hello"))
                .hasStackTraceContaining("Proxy configuration with name missing was requested")
                .hasStackTraceContaining("quarkus.proxy.\"missing\".host is not defined");
    }
}

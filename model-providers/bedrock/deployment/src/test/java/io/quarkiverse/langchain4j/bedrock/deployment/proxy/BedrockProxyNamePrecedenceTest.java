package io.quarkiverse.langchain4j.bedrock.deployment.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.LogRecord;

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

class BedrockProxyNamePrecedenceTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestCredentialsProvider.class, ForwardProxy.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            // Named proxy points to the forward proxy; the deprecated proxy-address points nowhere.
            // If the request goes through the forward proxy, the named configuration took precedence.
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.client.proxy-configuration-name", "local")
            .overrideRuntimeConfigKey("quarkus.proxy.local.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.local.port", String.valueOf(ForwardProxy.port()))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.client.proxy-address", "localhost:1")
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(BedrockProxyNamePrecedenceTest::verifyPrecedenceWarning);

    private static void verifyPrecedenceWarning(List<LogRecord> logRecords) {
        assertThat(logRecords.stream().map(LogRecord::getMessage))
                .anyMatch(message -> message.contains("The deprecated proxy properties are ignored"));
    }

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
    void should_prefer_named_proxy_over_deprecated_proxy_address() {
        mock.chat().reply(ChatReply.text("Routed through named proxy").usage(5, 4));

        var response = chatModel.chat("Hello");

        assertThat(response).isEqualTo("Routed through named proxy");
        // the mock is always the endpoint, so only the proxy's record shows the request went through it
        assertThat(ForwardProxy.forwardedUris())
                .containsExactly(mockUrl + "/bedrock/model/amazon.titan-text-express-v1/converse");
        assertThat(mock.chat().singleRequest().asChat().messages()).singleElement()
                .satisfies(m -> assertThat(m.text()).isEqualTo("Hello"));
    }
}

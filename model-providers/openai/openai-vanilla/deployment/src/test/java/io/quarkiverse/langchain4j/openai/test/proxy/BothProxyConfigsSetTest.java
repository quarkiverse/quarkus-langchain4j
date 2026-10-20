package io.quarkiverse.langchain4j.openai.test.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class BothProxyConfigsSetTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(ForwardProxy.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-configuration-name", "local")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-host", "proxy.example.com")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-port", "8080")
            .overrideRuntimeConfigKey("quarkus.proxy.local.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.local.port", String.valueOf(ForwardProxy.port()))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(BothProxyConfigsSetTest::verifyLogRecords);

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream().map(LogRecord::getMessage))
                .contains(
                        "Both 'proxy-configuration-name' (%s) and deprecated 'proxy-host' (%s) are set. The 'proxy-host' configuration will be ignored. Please remove 'proxy-host' and 'proxy-port' from your configuration.");
    }

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @ConfigProperty(name = "quarkus.langchain4j.mock-api.url")
    String mockUrl;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @AfterAll
    static void stopProxy() {
        ForwardProxy.stop();
    }

    @Test
    void shouldPrioritizeProxyConfigurationNameOverDeprecated() {
        SoftAssertions softly = new SoftAssertions();

        mock.chat().reply("response");
        String response = chatModel.chat("hello");
        softly.assertThat(response).isEqualTo("response");
        // the mock is always the endpoint, so only the proxy's record shows the request went through the named proxy
        softly.assertThat(ForwardProxy.forwardedUris()).containsExactly(mockUrl + "/v1/chat/completions");
        CapturedRequest request = mock.chat().singleRequest();

        softly.assertThat(request.header("Host")).contains("localhost");

        softly.assertAll();
    }
}

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

public class OpenAiProxyConfigurationTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(ForwardProxy.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-host", "localhost")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-port", String.valueOf(ForwardProxy.port()))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(OpenAiProxyConfigurationTest::verifyLogRecords);

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream().map(LogRecord::getMessage))
                .contains(
                        "Using deprecated 'proxy-host' configuration. Please migrate to 'proxy-configuration-name' using Quarkus Proxy Registry. The 'proxy-host', 'proxy-port', and 'proxy-type' properties will be removed in a future version.");
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
    void shouldUseDeprecatedProxyHost() {
        mock.chat().reply("response");
        String response = chatModel.chat("hello");

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(response).isEqualTo("response");
        // the mock is always the endpoint, so only the proxy's record shows the request went through the deprecated proxy
        softly.assertThat(ForwardProxy.forwardedUris()).containsExactly(mockUrl + "/v1/chat/completions");
        CapturedRequest request = mock.chat().singleRequest();
        softly.assertThat(request.header("User-Agent")).isEqualTo("langchain4j-openai");

        softly.assertAll();
    }
}

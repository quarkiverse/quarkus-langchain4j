package io.quarkiverse.langchain4j.openai.test.proxy;

import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class BothProxyConfigsSetTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-configuration-name", "local")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-host", "proxy.example.com")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-port", "8080")
            .overrideRuntimeConfigKey("quarkus.proxy.local.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.local.port", "${quarkus.wiremock.devservices.port}");

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void shouldPrioritizeProxyConfigurationNameOverDeprecated() {
        SoftAssertions softly = new SoftAssertions();

        setChatCompletionMessageContent("response");
        String response = chatModel.chat("hello");
        softly.assertThat(response).isEqualTo("response");
        LoggedRequest loggedRequest = singleLoggedRequest();

        test.assertLogRecords(logRecords -> {
            softly.assertThat(logRecords.stream().map(LogRecord::getMessage).toList())
                    .anyMatch(l -> l.contains(
                            "Both 'proxy-configuration-name' (local) and deprecated 'proxy-host' (proxy.example.com) are set. "
                                    +
                                    "The 'proxy-host' configuration will be ignored. Please remove 'proxy-host' and 'proxy-port' from your configuration."));
        });

        softly.assertThat(loggedRequest.getHost()).contains("localhost");

        softly.assertAll();
    }
}

package io.quarkiverse.langchain4j.openai.test.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

public class OpenAiProxyConfigurationTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-host", "localhost")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-port", "${quarkus.wiremock.devservices.port}")
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(OpenAiProxyConfigurationTest::verifyLogRecords);

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream().map(LogRecord::getMessage))
                .contains(
                        "Using deprecated 'proxy-host' configuration. Please migrate to 'proxy-configuration-name' using Quarkus Proxy Registry. The 'proxy-host', 'proxy-port', and 'proxy-type' properties will be removed in a future version.");
    }

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void shouldUseDeprecatedProxyHost() {
        setChatCompletionMessageContent("response");
        String response = chatModel.chat("hello");

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(response).isEqualTo("response");
        LoggedRequest loggedRequest = singleLoggedRequest();
        softly.assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("langchain4j-openai");

        softly.assertAll();
    }
}

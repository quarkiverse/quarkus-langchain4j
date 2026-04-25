package io.quarkiverse.langchain4j.openai.test.proxy;

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

public class DefaultProxyAvailableTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.proxy.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.port", "${quarkus.wiremock.devservices.port}");

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void shouldUseDefaultProxyFromRegistry() {
        setChatCompletionMessageContent("response");
        String response = chatModel.chat("hello");

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(response).isEqualTo("response");
        LoggedRequest loggedRequest = singleLoggedRequest();
        softly.assertThat(loggedRequest.getHost()).isEqualTo("localhost");

        test.assertLogRecords(logRecords -> {
            List<String> list = logRecords.stream().map(LogRecord::getMessage).toList();
            softly.assertThat(list).doesNotContain("Both 'proxy-configuration-name' ");
        });

        softly.assertAll();
    }
}

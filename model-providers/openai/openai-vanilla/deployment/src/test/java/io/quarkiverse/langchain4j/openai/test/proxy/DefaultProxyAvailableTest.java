package io.quarkiverse.langchain4j.openai.test.proxy;

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
import io.quarkus.test.QuarkusUnitTest;

public class DefaultProxyAvailableTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    "http://localhost:8282/v1") // nothing listens here — proxy must be used
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
        // Host header reflects the target (base-url), not the proxy.
        // The fact that WireMock received this request proves the proxy was used,
        // since nothing is actually listening on localhost:8282.
        softly.assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("langchain4j-openai");
        softly.assertThat(loggedRequest.getHeader("Host")).isEqualTo("localhost:8282");
        softly.assertAll();
    }

}

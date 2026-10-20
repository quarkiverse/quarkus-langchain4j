package io.quarkiverse.langchain4j.google.genai.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class GoogleGenAiTimeoutTest {

    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.api-key", "dummy")
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.timeout", "15s");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void honoursConfiguredTimeoutBeyondOkHttpDefault() {
        mock.chat().reply(ChatReply.text("Slow but fine").delay(Duration.ofMillis(11_000)));

        assertThat(chatModel.chat("hello")).isEqualTo("Slow but fine");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo(String.format("/google-genai/v1beta/models/%s:generateContent", CHAT_MODEL_ID));
        assertThat(request.query()).isNull();
    }
}

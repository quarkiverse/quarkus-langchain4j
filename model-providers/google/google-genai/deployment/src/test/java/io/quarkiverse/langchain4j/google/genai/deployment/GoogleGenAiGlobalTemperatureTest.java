package io.quarkiverse.langchain4j.google.genai.deployment;

import static org.assertj.core.api.Assertions.assertThat;

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

public class GoogleGenAiGlobalTemperatureTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.temperature", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.api-key", "dummy");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void globalTemperatureIsStillHonored() {
        mock.chat().reply(ChatReply.text("hi").usage(1, 2));

        chatModel.chat("hello");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).endsWith(":generateContent");
        assertThat(request.body()).contains("\"temperature\"").contains("0.5");
    }
}

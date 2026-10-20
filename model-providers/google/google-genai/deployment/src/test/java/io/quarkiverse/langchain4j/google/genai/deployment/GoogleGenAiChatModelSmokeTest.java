package io.quarkiverse.langchain4j.google.genai.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class GoogleGenAiChatModelSmokeTest {

    private static final String API_KEY = "dummy";
    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.log-requests", "true");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(GoogleGenAiChatModel.class);

        mock.chat().reply(ChatReply.text("Nice to meet you").usage(11, 37));

        String response = chatModel.chat("hello");
        assertThat(response).isEqualTo("Nice to meet you");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo(String.format("/google-genai/v1beta/models/%s:generateContent", CHAT_MODEL_ID));
        assertThat(request.query()).isNull();
        assertThat(request.header("x-goog-api-key")).isEqualTo(API_KEY);
        assertThat(request.body()).contains("hello");
    }

}

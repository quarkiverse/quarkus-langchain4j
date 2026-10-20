package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AiGeminiNamedModelAuthProviderTest {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // set to empty on purpose: the provider installs its ModelAuthProvider filter only without an api key, and the
            // mock leaves a key alone when it is set, even to an empty value
            .overrideConfigKey("quarkus.langchain4j.ai.gemini.my-gemini.api-key", "")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.my-gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    @ModelName("my-gemini")
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void authProviderIsSelectedByConfigName() {
        mock.chat().reply(ChatReply.text("Hi").usage(1, 1));

        chatModel.chat("hello");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo(String.format("/ai-gemini/v1beta/models/%s:generateContent", CHAT_MODEL_ID));
        assertThat(request.header("Authorization")).isEqualTo("Bearer my-gemini-key");
    }

    @Singleton
    @ModelName("my-gemini")
    public static class MyGeminiAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer my-gemini-key";
        }
    }
}

package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AiGeminiNamedModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset("quarkus.langchain4j.ai.gemini.a1.chat-model.model-id=gemini-named\n"),
                            "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.a1.chat-model.provider", "ai-gemini");

    @Inject
    @ModelName("a1")
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @Test
    void namedModelReachesTheMock() {
        mock.reset();
        mock.chat().reply("named");

        assertThat(chatModel.chat("hi")).isEqualTo("named");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/ai-gemini/v1beta/models/gemini-named:generateContent");
        assertThat(r.asChat().modelName()).isEqualTo("gemini-named");
        assertThat(r.header("x-goog-api-key")).isEqualTo("mock-api-key");
    }
}

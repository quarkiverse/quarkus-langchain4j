package io.quarkiverse.langchain4j.ollama.deployment;

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

public class OllamaNamedModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.ollama.o1.chat-model.model-id=qwen-named\n"),
                            "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.o1.chat-model.provider", "ollama");

    @Inject
    @ModelName("o1")
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @Test
    void namedModelReachesTheMock() {
        mock.reset();
        mock.chat().reply("named");

        assertThat(chatModel.chat("hi")).isEqualTo("named");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/ollama/api/chat");
        assertThat(r.asChat().modelName()).isEqualTo("qwen-named");
    }
}

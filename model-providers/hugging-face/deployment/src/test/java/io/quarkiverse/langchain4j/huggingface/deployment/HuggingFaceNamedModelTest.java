package io.quarkiverse.langchain4j.huggingface.deployment;

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

public class HuggingFaceNamedModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset("quarkus.langchain4j.huggingface.h1.chat-model.temperature=0.3\n"
                                    + "quarkus.langchain4j.huggingface.h1.chat-model.return-full-text=true\n"),
                            "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.h1.chat-model.provider", "huggingface");

    @Inject
    @ModelName("h1")
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @Test
    void namedModelReachesTheMock() {
        mock.reset();
        mock.chat().reply(" there");

        // return_full_text: the endpoint prepends the prompt, as the real one does
        assertThat(chatModel.chat("hi")).isEqualTo("hi there");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/huggingface/chat");
        assertThat(r.asChat().temperature()).isEqualTo(0.3);
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-api-key");
    }
}

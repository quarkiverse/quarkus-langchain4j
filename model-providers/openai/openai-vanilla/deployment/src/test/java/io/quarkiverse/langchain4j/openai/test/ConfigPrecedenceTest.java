package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class ConfigPrecedenceTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.openai.base-url=https://real.example.invalid/v1\n"
                            + "quarkus.langchain4j.openai.api-key=user-key\n"), "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "openai");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @Test
    void mockUrlBeatsApplicationPropertiesButUserKeyIsKept() {
        mock.reset();
        mock.chat().reply("ok");

        assertThat(chatModel.chat("hi")).isEqualTo("ok");
        assertThat(mock.chat().singleRequest().header("Authorization")).isEqualTo("Bearer user-key");
    }
}

package io.quarkiverse.langchain4j.openai.test;

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

public class NamedModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.openai.m1.chat-model.model-name=gpt-4o-mini\n"),
                            "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.m1.chat-model.provider", "openai");

    @Inject
    @ModelName("m1")
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @Test
    void namedModelReachesTheMock() {
        mock.reset();
        mock.chat().reply("named");

        assertThat(chatModel.chat("hi")).isEqualTo("named");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.asChat().modelName()).isEqualTo("gpt-4o-mini");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-api-key");
    }
}

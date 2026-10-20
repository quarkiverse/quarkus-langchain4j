package io.quarkiverse.langchain4j.azure.openai.test;

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

/**
 * An application that authenticates with {@code ad-token} must still start: the mock must not add an {@code api-key}
 * next to it, at the default level, per model kind, or for a named model.
 */
public class AzureOpenAiAdTokenTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.azure-openai.ad-token=default-token
                            quarkus.langchain4j.azure-openai.a1.chat-model.ad-token=named-token
                            """), "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "azure-openai")
            .overrideConfigKey("quarkus.langchain4j.a1.chat-model.provider", "azure-openai");

    @Inject
    ChatModel chatModel;

    @Inject
    @ModelName("a1")
    ChatModel namedChatModel;

    @Inject
    MockModelServer mock;

    @Test
    void defaultModelSendsTheAdToken() {
        mock.reset();
        mock.chat().reply("ok");

        assertThat(chatModel.chat("hi")).isEqualTo("ok");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.header("Authorization")).isEqualTo("Bearer default-token");
        assertThat(r.header("api-key")).isNull();
    }

    @Test
    void namedModelSendsItsPerKindAdToken() {
        mock.reset();
        mock.chat().reply("ok");

        assertThat(namedChatModel.chat("hi")).isEqualTo("ok");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.header("Authorization")).isEqualTo("Bearer named-token");
        assertThat(r.header("api-key")).isNull();
    }
}

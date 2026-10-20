package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class ModelAuthProviderSmokeTest {

    // the empty api-key keeps the mock from filling one in: the ModelAuthProvider must be the only credential
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.azure-openai.api-key", "");

    @Inject
    ChatModel chatLanguageModel;

    @Inject
    StreamingChatModel streamingChatLanguageModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void testChatLanguageModel() {
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(AzureOpenAiChatModel.class);
        assertThat(ClientProxy.unwrap(streamingChatLanguageModel)).isInstanceOf(AzureOpenAiStreamingChatModel.class);

        mock.chat().reply("Hello there");

        String response = chatLanguageModel.chat("hello");
        assertThat(response).isEqualTo("Hello there");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.header("Authorization")).isEqualTo("Bearer token");
        assertThat(request.header("api-key")).isNull();
    }

    @ApplicationScoped
    public static class DummyModelAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }
            return "Bearer token";
        }
    }
}

package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OpenAiChatLanguageModelSmokeTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");
    @Inject
    ChatModel chatLanguageModel;

    @Inject
    MockModelServer mock;

    @ConfigProperty(name = "quarkus.langchain4j.openai.base-url")
    String baseUrl;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void test() {
        mock.chat().reply("whatever");
        String response = chatLanguageModel.chat("hello");
        assertThat(response).isEqualTo("whatever");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.header("User-Agent")).isEqualTo("langchain4j-openai");
    }

    @Test
    void customHeaders() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder().baseUrl(baseUrl)
                .apiKey("whatever")
                .modelName("gpt-4o-mini")
                .customHeaders(Map.of("Foo", "Bar"))
                .build();

        mock.chat().reply("whatever");
        String response = chatModel.chat("hello");
        assertThat(response).isEqualTo("whatever");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.header("User-Agent")).isEqualTo("langchain4j-openai");
        assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
        assertThat(request.header("Foo")).isEqualTo("Bar");
    }
}

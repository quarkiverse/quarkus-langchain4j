package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class ModelBuilderCustomizerNamedTest {

    @ApplicationScoped
    @ModelName("my-model")
    public static class NamedCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.customHeaders(Map.of("X-Custom-Named", "named-value"));
        }
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(NamedCustomizer.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideConfigKey("quarkus.langchain4j.openai.my-model.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "defaultKey")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.my-model.api-key", "namedKey");

    @Inject
    ChatModel defaultModel;

    @Inject
    @ModelName("my-model")
    ChatModel namedModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setup() {
        mock.reset();
        mock.chat().reply("hello");
    }

    @Test
    void customizerAppliedToNamedModel() {
        assertThat(namedModel.chat("test")).isEqualTo("hello");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.header("Authorization")).isEqualTo("Bearer namedKey");
        assertThat(request.header("X-Custom-Named")).isEqualTo("named-value");
    }

    @Test
    void customizerNotAppliedToDefaultModel() {
        assertThat(defaultModel.chat("test")).isEqualTo("hello");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.header("Authorization")).isEqualTo("Bearer defaultKey");
        assertThat(request.header("X-Custom-Named")).isNull();
    }
}

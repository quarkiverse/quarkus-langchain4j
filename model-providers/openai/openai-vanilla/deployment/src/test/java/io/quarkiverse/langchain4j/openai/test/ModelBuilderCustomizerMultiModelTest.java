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

/**
 * Tests that customizers are correctly scoped when both default and named models are configured.
 * <p>
 * Scenario: A default customizer (no qualifier) and a named customizer ({@code @ModelName("second")})
 * coexist. The default customizer should only apply to the default model, and the named customizer
 * should only apply to the named model.
 */
public class ModelBuilderCustomizerMultiModelTest {

    @ApplicationScoped
    public static class DefaultCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.customHeaders(Map.of("X-Default-Customizer", "default-value"));
        }
    }

    @ApplicationScoped
    @ModelName("second")
    public static class SecondCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.customHeaders(Map.of("X-Second-Customizer", "second-value"));
        }
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(DefaultCustomizer.class, SecondCustomizer.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideConfigKey("quarkus.langchain4j.openai.second.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "defaultKey")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.second.api-key", "secondKey");

    @Inject
    ChatModel defaultModel;

    @Inject
    @ModelName("second")
    ChatModel secondModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setup() {
        mock.reset();
        mock.chat().reply("hello");
    }

    @Test
    void defaultCustomizerAppliedToDefaultModel() {
        assertThat(defaultModel.chat("test")).isEqualTo("hello");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.header("Authorization")).isEqualTo("Bearer defaultKey");
        assertThat(request.header("X-Default-Customizer")).isEqualTo("default-value");
        assertThat(request.header("X-Second-Customizer")).isNull();
    }

    @Test
    void namedCustomizerAppliedToNamedModel() {
        assertThat(secondModel.chat("test")).isEqualTo("hello");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.header("Authorization")).isEqualTo("Bearer secondKey");
        assertThat(request.header("X-Second-Customizer")).isEqualTo("second-value");
        assertThat(request.header("X-Default-Customizer")).isNull();
    }
}

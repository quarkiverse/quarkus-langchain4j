package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that customizers are correctly scoped when both default and named models are configured.
 * <p>
 * Scenario: A default customizer (no qualifier) and a named customizer ({@code @ModelName("second")})
 * coexist. The default customizer should only apply to the default model, and the named customizer
 * should only apply to the named model.
 */
public class ModelBuilderCustomizerMultiModelTest extends OpenAiBaseTest {

    @ApplicationScoped
    public static class DefaultCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.customHeaders(java.util.Map.of("X-Default-Customizer", "default-value"));
        }
    }

    @ApplicationScoped
    @ModelName("second")
    public static class SecondCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.customHeaders(java.util.Map.of("X-Second-Customizer", "second-value"));
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(DefaultCustomizer.class, SecondCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "defaultKey")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.second.api-key", "secondKey")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.second.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel defaultModel;

    @Inject
    @ModelName("second")
    ChatModel secondModel;

    @BeforeEach
    void setup() {
        setChatCompletionMessageContent("hello");
        resetRequests();
    }

    @Test
    void defaultCustomizerAppliedToDefaultModel() {
        defaultModel.chat("test");

        LoggedRequest request = singleLoggedRequest();
        assertThat(request.getHeader("X-Default-Customizer")).isEqualTo("default-value");
        assertThat(request.getHeader("X-Second-Customizer")).isNull();
    }

    @Test
    void namedCustomizerAppliedToNamedModel() {
        secondModel.chat("test");

        LoggedRequest request = singleLoggedRequest();
        assertThat(request.getHeader("X-Second-Customizer")).isEqualTo("second-value");
        assertThat(request.getHeader("X-Default-Customizer")).isNull();
    }
}

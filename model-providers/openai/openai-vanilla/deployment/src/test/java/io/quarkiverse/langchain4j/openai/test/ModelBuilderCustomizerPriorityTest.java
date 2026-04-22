package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that customizer priority ordering works correctly.
 * A higher-priority customizer runs first; a lower-priority one runs after and can override.
 */
public class ModelBuilderCustomizerPriorityTest extends OpenAiBaseTest {

    @ApplicationScoped
    public static class LowPriorityCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.modelName("low-priority-model");
        }

        @Override
        public int priority() {
            return -10;
        }
    }

    @ApplicationScoped
    public static class HighPriorityCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.modelName("high-priority-model");
        }

        @Override
        public int priority() {
            return 10;
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(LowPriorityCustomizer.class, HighPriorityCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @Test
    void lowerPriorityCustomizerRunsLastAndWins() throws Exception {
        setChatCompletionMessageContent("hello");
        chatModel.chat("test");

        LoggedRequest request = singleLoggedRequest();
        java.util.Map<String, Object> body = getRequestAsMap(request.getBody());
        assertThat(body.get("model")).isEqualTo("low-priority-model");
    }
}

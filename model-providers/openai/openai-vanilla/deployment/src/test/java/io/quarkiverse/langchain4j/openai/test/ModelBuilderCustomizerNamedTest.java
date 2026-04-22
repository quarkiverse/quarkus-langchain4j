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

public class ModelBuilderCustomizerNamedTest extends OpenAiBaseTest {

    @ApplicationScoped
    @ModelName("my-model")
    public static class NamedCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.customHeaders(java.util.Map.of("X-Custom-Named", "named-value"));
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(NamedCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "defaultKey")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.my-model.api-key", "namedKey")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.my-model.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel defaultModel;

    @Inject
    @ModelName("my-model")
    ChatModel namedModel;

    @BeforeEach
    void setup() {
        setChatCompletionMessageContent("hello");
        resetRequests();
    }

    @Test
    void customizerAppliedToNamedModel() {
        namedModel.chat("test");

        LoggedRequest request = singleLoggedRequest();
        assertThat(request.getHeader("X-Custom-Named")).isEqualTo("named-value");
    }

    @Test
    void customizerNotAppliedToDefaultModel() {
        defaultModel.chat("test");

        LoggedRequest request = singleLoggedRequest();
        assertThat(request.getHeader("X-Custom-Named")).isNull();
    }
}

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

public class ModelBuilderCustomizerTest extends OpenAiBaseTest {

    @ApplicationScoped
    public static class MyCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.customHeaders(java.util.Map.of("X-Custom-From-Customizer", "customized"));
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @Test
    void customizerIsApplied() {
        setChatCompletionMessageContent("hello");
        chatModel.chat("test");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("X-Custom-From-Customizer")).isEqualTo("customized");
    }
}

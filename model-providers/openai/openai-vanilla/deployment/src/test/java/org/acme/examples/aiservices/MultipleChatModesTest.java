package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleChatModesTest extends OpenAiBaseTest {

    public static final String MESSAGE_CONTENT = "Tell me a joke about developers";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "defaultKey")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.model1.api-key", "key1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.model1.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.model2.api-key", "key2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.model2.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @BeforeEach
    void setup() {
        setChatCompletionMessageContent(MESSAGE_CONTENT);
        resetRequests();
    }

    @Inject
    ChatLanguageModel chatWithDefaultModel;

    @Inject
    @ModelName("model1")
    ChatLanguageModel chatWithModel1;

    @Inject
    @ModelName("model2")
    ChatLanguageModel chatWithModel2;

    @Test
    @ActivateRequestContext
    public void testDefaultModel() throws IOException {
        String result = chatWithDefaultModel.generate(MESSAGE_CONTENT);
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), MESSAGE_CONTENT);
    }

    @Test
    @ActivateRequestContext
    public void testNamedModel1() throws IOException {
        String result = chatWithModel1.generate(MESSAGE_CONTENT);
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), MESSAGE_CONTENT);
    }

    @Test
    @ActivateRequestContext
    public void testNamedModel2() throws IOException {
        String result = chatWithModel2.generate(MESSAGE_CONTENT);
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), MESSAGE_CONTENT);
    }

}

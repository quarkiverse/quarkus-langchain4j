package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleChatModesWithoutDefaultTest extends OpenAiBaseTest {

    public static final String MESSAGE_CONTENT = "Tell me a joke about developers";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.model1.api-key", "key1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.model1.endpoint",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.model2.api-key", "key2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.model2.endpoint",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @BeforeEach
    void setup() {
        setChatCompletionMessageContent(MESSAGE_CONTENT);
        resetRequests();
    }

    @Inject
    @ModelName("model1")
    ChatModel chatWithModel1;

    @Inject
    @ModelName("model2")
    ChatModel chatWithModel2;

    @Test
    @ActivateRequestContext
    public void testNamedModel1() throws IOException {
        String result = chatWithModel1.chat(MESSAGE_CONTENT);
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), MESSAGE_CONTENT);
    }

    @Test
    @ActivateRequestContext
    public void testNamedModel2() throws IOException {
        String result = chatWithModel2.chat(MESSAGE_CONTENT);
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), MESSAGE_CONTENT);
    }

}

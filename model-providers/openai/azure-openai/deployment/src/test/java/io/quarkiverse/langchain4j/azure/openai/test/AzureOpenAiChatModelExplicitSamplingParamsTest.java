package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AzureOpenAiChatModelExplicitSamplingParamsTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.temperature", "0.3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.top-p", "0.8")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void explicitlyConfiguredValuesAreSent() throws IOException {
        chatModel.chat("hello");

        Map<String, Object> requestBody = getRequestAsMap();
        assertThat(requestBody).containsEntry("temperature", 0.3).containsEntry("top_p", 0.8);
    }

    @Test
    void streamingModelAlsoSendsThem() throws IOException, InterruptedException {
        var latch = new CountDownLatch(1);
        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.MINUTES);

        Map<String, Object> requestBody = getRequestAsMap();
        assertThat(requestBody).containsEntry("temperature", 0.3).containsEntry("top_p", 0.8);
    }
}

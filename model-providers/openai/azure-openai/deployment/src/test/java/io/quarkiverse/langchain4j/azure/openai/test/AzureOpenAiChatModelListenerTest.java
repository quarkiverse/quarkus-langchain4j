package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AzureOpenAiChatModelListenerTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CapturingChatModelListener.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.deployment-name", "my-gpt4");

    @Inject
    ChatModel chatModel;

    @Inject
    CapturingChatModelListener listener;

    @Test
    void listenerReceivesModelName() {
        chatModel.chat("hello");
        assertThat(listener.wasCalled()).as("listener.onRequest was called").isTrue();
        assertThat(listener.getRequestModelName()).isEqualTo("my-gpt4");
    }

    @ApplicationScoped
    public static class CapturingChatModelListener implements ChatModelListener {
        private final AtomicBoolean called = new AtomicBoolean(false);
        private final AtomicReference<String> requestModelName = new AtomicReference<>();

        public boolean wasCalled() {
            return called.get();
        }

        public String getRequestModelName() {
            return requestModelName.get();
        }

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            called.set(true);
            requestModelName.set(requestContext.chatRequest().parameters().modelName());
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
        }
    }
}

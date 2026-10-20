package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AzureOpenAiChatModelListenerTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CapturingChatModelListener.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.deployment-name", "my-gpt4");

    @Inject
    ChatModel chatModel;

    @Inject
    CapturingChatModelListener listener;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void listenerReceivesModelName() {
        mock.chat().reply("ok");

        assertThat(chatModel.chat("hello")).isEqualTo("ok");
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

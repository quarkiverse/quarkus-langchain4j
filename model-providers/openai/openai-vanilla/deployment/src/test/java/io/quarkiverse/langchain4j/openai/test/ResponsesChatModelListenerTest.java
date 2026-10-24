package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * {@link ChatModelListener} beans are wired into the model in Responses mode too.
 */
public class ResponsesChatModelListenerTest {

    @ApplicationScoped
    public static class RecordingListener implements ChatModelListener {

        // accessed through methods: the test holds a client proxy, whose own fields are not the bean's
        private final List<String> requestedModels = new CopyOnWriteArrayList<>();
        private final List<String> responseTexts = new CopyOnWriteArrayList<>();
        private final List<Throwable> errors = new CopyOnWriteArrayList<>();

        List<String> requestedModels() {
            return requestedModels;
        }

        List<String> responseTexts() {
            return responseTexts;
        }

        List<Throwable> errors() {
            return errors;
        }

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            requestedModels.add(requestContext.chatRequest().modelName());
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            responseTexts.add(responseContext.chatResponse().aiMessage().text());
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
            errors.add(errorContext.error());
        }

        void clear() {
            requestedModels.clear();
            responseTexts.clear();
            errors.clear();
        }
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(RecordingListener.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key");

    @Inject
    ChatModel chatModel;

    @Inject
    RecordingListener listener;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
        listener.clear();
    }

    @Test
    void listenerSeesRequestAndResponse() {
        mock.chat().reply("hello");

        assertThat(chatModel.chat("hi")).isEqualTo("hello");

        assertThat(mock.chat().singleRequest().path()).isEqualTo("/v1/responses");
        assertThat(listener.requestedModels()).containsExactly("gpt-6-luna");
        assertThat(listener.responseTexts()).containsExactly("hello");
        assertThat(listener.errors()).isEmpty();
    }

    @Test
    void listenerSeesError() {
        mock.chat().fail(500, "boom");

        assertThatThrownBy(() -> chatModel.chat("hi")).isInstanceOf(InternalServerException.class);

        assertThat(mock.requests()).isNotEmpty()
                .allSatisfy(request -> assertThat(request.path()).isEqualTo("/v1/responses"));
        assertThat(listener.requestedModels()).containsExactly("gpt-6-luna");
        assertThat(listener.responseTexts()).isEmpty();
        assertThat(listener.errors()).singleElement().isInstanceOf(InternalServerException.class);
    }
}

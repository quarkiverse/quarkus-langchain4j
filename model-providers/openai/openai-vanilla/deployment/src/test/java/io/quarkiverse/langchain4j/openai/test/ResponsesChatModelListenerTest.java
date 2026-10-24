package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * {@link ChatModelListener} beans are wired into the model in Responses mode too.
 */
public class ResponsesChatModelListenerTest extends OpenAiBaseTest {

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
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @Inject
    RecordingListener listener;

    @BeforeEach
    void setUp() {
        resetMappings();
        resetRequests();
        listener.clear();
    }

    @Test
    void listenerSeesRequestAndResponse() {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "resp_1",
                                  "model": "gpt-6-luna",
                                  "status": "completed",
                                  "output": [
                                    {
                                      "type": "message",
                                      "role": "assistant",
                                      "content": [ { "type": "output_text", "text": "hello" } ]
                                    }
                                  ],
                                  "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                }
                                """)));

        assertThat(chatModel.chat("hi")).isEqualTo("hello");

        assertThat(listener.requestedModels()).containsExactly("gpt-6-luna");
        assertThat(listener.responseTexts()).containsExactly("hello");
        assertThat(listener.errors()).isEmpty();
    }

    @Test
    void listenerSeesError() {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error": {"message": "boom", "type": "server_error"}}
                                """)));

        assertThatThrownBy(() -> chatModel.chat("hi")).isInstanceOf(InternalServerException.class);

        assertThat(listener.requestedModels()).containsExactly("gpt-6-luna");
        assertThat(listener.responseTexts()).isEmpty();
        assertThat(listener.errors()).singleElement().isInstanceOf(InternalServerException.class);
    }
}

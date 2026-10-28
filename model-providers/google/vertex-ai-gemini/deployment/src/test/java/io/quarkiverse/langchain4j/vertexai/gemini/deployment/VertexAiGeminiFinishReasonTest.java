package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiFinishReasonTest {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    ChatModel chatModel;

    @Inject
    RecordingListener listener;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
        listener.responses.clear();
    }

    @Test
    void toolCallTurnReportsToolExecution() {
        // Gemini ends a function call turn with STOP, as the mock renders it
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris")));

        ChatResponse response = chatModel.chat(UserMessage.from("What is the weather in Paris?"));

        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    }

    @Test
    void listenerReceivesToolExecutionFinishReason() {
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris")));

        chatModel.chat(UserMessage.from("What is the weather in Paris?"));

        assertThat(listener.responses).singleElement()
                .satisfies(context -> assertThat(context.chatResponse().finishReason())
                        .isEqualTo(FinishReason.TOOL_EXECUTION));
    }

    @Test
    void listenerReceivesFinishReasonForTextResponse() {
        mock.chat().reply("Hi");

        ChatResponse response = chatModel.chat(UserMessage.from("hello"));

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(listener.responses).singleElement()
                .satisfies(context -> assertThat(context.chatResponse().finishReason())
                        .isEqualTo(FinishReason.STOP));
    }

    @Singleton
    public static class RecordingListener implements ChatModelListener {

        final List<ChatModelResponseContext> responses = new CopyOnWriteArrayList<>();

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            responses.add(responseContext);
        }
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}

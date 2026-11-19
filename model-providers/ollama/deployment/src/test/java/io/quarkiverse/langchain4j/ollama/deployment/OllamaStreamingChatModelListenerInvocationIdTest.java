package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Context;

/**
 * The Ollama streaming model notifies its {@link ChatModelListener}s itself, the response and error ones from the
 * thread that delivers the stream. Their contexts must carry the id of the AI service invocation that made the call.
 */
public class OllamaStreamingChatModelListenerInvocationIdTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Calculator.class, Recorder.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "ollama");

    @Singleton
    @RegisterAiService(tools = Calculator.class)
    interface AiService {
        Multi<String> streaming(@UserMessage String text);
    }

    @Singleton
    static class Calculator {
        @Tool("Execute the sum of two numbers")
        public int sum(int firstNumber, int secondNumber) {
            return firstNumber + secondNumber;
        }
    }

    @Singleton
    public static class Recorder implements ChatModelListener {

        final List<UUID> startedInvocationIds = new CopyOnWriteArrayList<>();
        final List<UUID> requestInvocationIds = new CopyOnWriteArrayList<>();
        final List<UUID> responseInvocationIds = new CopyOnWriteArrayList<>();
        final List<UUID> errorInvocationIds = new CopyOnWriteArrayList<>();
        final List<Boolean> responsesOnEventLoop = new CopyOnWriteArrayList<>();

        void onStarted(@Observes AiServiceStartedEvent event) {
            startedInvocationIds.add(event.invocationContext().invocationId());
        }

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            requestInvocationIds.add(requestContext.invocationId());
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            responseInvocationIds.add(responseContext.invocationId());
            responsesOnEventLoop.add(Context.isOnEventLoopThread());
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
            errorInvocationIds.add(errorContext.invocationId());
        }

        UUID singleInvocationId() {
            assertThat(startedInvocationIds).hasSize(1);
            assertThat(startedInvocationIds.get(0)).isNotNull();
            return startedInvocationIds.get(0);
        }

        void clear() {
            startedInvocationIds.clear();
            requestInvocationIds.clear();
            responseInvocationIds.clear();
            errorInvocationIds.clear();
            responsesOnEventLoop.clear();
        }
    }

    @Inject
    AiService aiService;

    @Inject
    Recorder recorder;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
        recorder.clear();
    }

    @Test
    void listenerContextsCarryTheInvocationIdAcrossToolRoundTrips() {
        mock.chat().replies(List.of(
                ChatReply.toolCall("sum", Map.of("firstNumber", 1, "secondNumber", 1)),
                ChatReply.text("The result is 2")));

        List<String> result = aiService.streaming("1 + 1").collect().asList().await().atMost(Duration.ofSeconds(30));
        assertThat(String.join("", result)).isEqualTo("The result is 2");
        assertThat(mock.chat().requests()).hasSize(2);

        UUID invocationId = recorder.singleInvocationId();
        // the call that asked for the tool, and the one that got its result
        assertThat(recorder.requestInvocationIds).containsExactly(invocationId, invocationId);
        // the model does not notify onResponse for a response that asks for tools, so only the last one is there
        assertThat(recorder.responseInvocationIds).containsExactly(invocationId);
        // and it notified it from the thread that delivered the stream
        assertThat(recorder.responsesOnEventLoop).containsExactly(true);
    }

    @Test
    void errorListenerContextCarriesTheInvocationId() {
        mock.chat().fail(400, "bad request");

        assertThat(aiService.streaming("1 + 1").collect().asList().subscribeAsCompletionStage())
                .failsWithin(Duration.ofSeconds(30));

        UUID invocationId = recorder.singleInvocationId();
        assertThat(recorder.requestInvocationIds).containsExactly(invocationId);
        assertThat(recorder.errorInvocationIds).containsExactly(invocationId);
        assertThat(recorder.responseInvocationIds).isEmpty();
    }
}

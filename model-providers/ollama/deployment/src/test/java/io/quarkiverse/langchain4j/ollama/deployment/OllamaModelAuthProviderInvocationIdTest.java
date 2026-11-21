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
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Context;

/**
 * A {@link ModelAuthProvider} runs inside the HTTP call to the model, on whatever thread the REST client uses. The
 * id of the AI service invocation does not depend on it: it is read when the model call starts and carried in the
 * listener contexts from there.
 */
public class OllamaModelAuthProviderInvocationIdTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Calculator.class, Recorder.class, RecordingAuthProvider.class))
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
    public static class RecordingAuthProvider implements ModelAuthProvider {

        final List<String> threads = new CopyOnWriteArrayList<>();

        @Override
        public String getAuthorization(Input input) {
            threads.add(Thread.currentThread().getName());
            return "Bearer test";
        }
    }

    @Singleton
    public static class Recorder implements ChatModelListener {

        final List<UUID> startedInvocationIds = new CopyOnWriteArrayList<>();
        final List<UUID> requestInvocationIds = new CopyOnWriteArrayList<>();
        final List<UUID> responseInvocationIds = new CopyOnWriteArrayList<>();
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
    }

    @Inject
    AiService aiService;

    @Inject
    Recorder recorder;

    @Inject
    RecordingAuthProvider authProvider;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void invocationIdDoesNotDependOnTheThreadOfTheAuthProvider() {
        mock.chat().replies(List.of(
                ChatReply.toolCall("sum", Map.of("firstNumber", 1, "secondNumber", 1)),
                ChatReply.text("The result is 2")));

        List<String> result = aiService.streaming("1 + 1").collect().asList().await().atMost(Duration.ofSeconds(30));
        assertThat(String.join("", result)).isEqualTo("The result is 2");

        // both model calls carried the header set by the auth provider
        assertThat(mock.chat().requests()).hasSize(2);
        for (CapturedRequest request : mock.chat().requests()) {
            assertThat(request.header("Authorization")).isEqualTo("Bearer test");
        }

        // the auth provider was used for both model calls, on two different threads (the caller thread, then the
        // thread that executed the tool), and the response was delivered on the event loop
        assertThat(authProvider.threads).hasSize(2);
        assertThat(authProvider.threads.get(0)).isNotEqualTo(authProvider.threads.get(1));
        assertThat(recorder.responsesOnEventLoop).containsExactly(true);

        assertThat(recorder.startedInvocationIds).hasSize(1);
        UUID invocationId = recorder.startedInvocationIds.get(0);
        assertThat(recorder.requestInvocationIds).containsExactly(invocationId, invocationId);
        // the model does not notify onResponse for a response that asks for tools, so only the last one is there
        assertThat(recorder.responseInvocationIds).containsExactly(invocationId);
    }
}

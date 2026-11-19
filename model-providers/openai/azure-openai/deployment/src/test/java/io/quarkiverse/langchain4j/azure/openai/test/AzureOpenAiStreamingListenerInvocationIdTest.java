package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
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

/**
 * The Azure OpenAI streaming model notifies its {@link ChatModelListener}s itself, the response and error ones from
 * the thread that delivers the stream. Their contexts must carry the id of the AI service invocation that made the
 * call.
 */
public class AzureOpenAiStreamingListenerInvocationIdTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @Singleton
    @RegisterAiService
    interface AiService {
        Multi<String> chat(@UserMessage String message);
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
    void responseListenerContextCarriesTheInvocationId() {
        mock.chat().reply(ChatReply.text("Hi"));

        List<String> result = aiService.chat("hello").collect().asList().await().atMost(Duration.ofSeconds(30));
        assertThat(String.join("", result)).isEqualTo("Hi");
        assertThat(mock.chat().requests()).hasSize(1);

        UUID invocationId = recorder.singleInvocationId();
        assertThat(recorder.requestInvocationIds).containsExactly(invocationId);
        assertThat(recorder.responseInvocationIds).containsExactly(invocationId);
        assertThat(recorder.errorInvocationIds).isEmpty();
        // the response listener was notified from the thread that delivered the stream, not the caller thread
        assertThat(recorder.responseThreads).singleElement().isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    void errorListenerContextCarriesTheInvocationId() {
        mock.chat().fail(400, "bad request");

        assertThat(aiService.chat("hello").collect().asList().subscribeAsCompletionStage())
                .failsWithin(Duration.ofSeconds(30));

        UUID invocationId = recorder.singleInvocationId();
        assertThat(recorder.requestInvocationIds).containsExactly(invocationId);
        assertThat(recorder.errorInvocationIds).containsExactly(invocationId);
        assertThat(recorder.responseInvocationIds).isEmpty();
    }

    @Singleton
    public static class Recorder implements ChatModelListener {

        final List<UUID> startedInvocationIds = new CopyOnWriteArrayList<>();
        final List<UUID> requestInvocationIds = new CopyOnWriteArrayList<>();
        final List<UUID> responseInvocationIds = new CopyOnWriteArrayList<>();
        final List<UUID> errorInvocationIds = new CopyOnWriteArrayList<>();
        final List<String> responseThreads = new CopyOnWriteArrayList<>();

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
            responseThreads.add(Thread.currentThread().getName());
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
            responseThreads.clear();
        }
    }
}

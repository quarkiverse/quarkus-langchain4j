package io.quarkiverse.langchain4j.test.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.observability.AiServiceSelector;
import io.quarkus.test.QuarkusUnitTest;

/**
 * LangChain4j 1.23.0 added {@link AiServiceInvocationSummaryEvent}, a composite event that the
 * {@code AiServiceListenerRegistrar} synthesises itself: it records every event fired for an invocation and, when the
 * invocation ends, fires a single summary carrying all of them.
 * <p>
 * The extension bridges LangChain4j events to CDI through the hardcoded {@code AiServiceEvents} enum, so an event
 * missing from that enum never reaches an {@code @Observes} method. This test asserts the summary event is observable.
 */
public class AiServiceInvocationSummaryEventTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, OtherAiService.class,
                            MyChatModel.class, MyChatModelSupplier.class,
                            FailingChatModel.class, FailingChatModelSupplier.class,
                            InvocationSummaryRecorder.class));

    @Inject
    MyAiService service;

    @Inject
    OtherAiService otherService;

    @Inject
    InvocationSummaryRecorder recorder;

    @BeforeEach
    void clearRecorder() {
        recorder.clear();
    }

    @Test
    @ActivateRequestContext
    void successfulInvocationFiresSummaryEvent() {
        assertThat(service.chat("hello")).isEqualTo("Hi!");

        assertThat(recorder.allEvents()).hasSize(1);
        var summary = recorder.allEvents().get(0);

        assertThat(summary.invocationContext().interfaceName()).isEqualTo(MyAiService.class.getName());
        assertThat(summary.invocationContext().methodName()).isEqualTo("chat");

        // the summary carries the whole lifecycle of the invocation, in the order the events were fired,
        // and the terminal event is the one that ended it
        assertThat(summary.events()).first().isInstanceOf(AiServiceStartedEvent.class);
        assertThat(summary.events()).last().isInstanceOf(AiServiceCompletedEvent.class);
        assertThat(summary.events()).hasAtLeastOneElementOfType(AiServiceResponseReceivedEvent.class);

        // every recorded event belongs to the invocation the summary is for
        assertThat(summary.events())
                .allSatisfy(event -> assertThat(event.invocationContext().invocationId())
                        .isEqualTo(summary.invocationContext().invocationId()));
    }

    @Test
    @ActivateRequestContext
    void failedInvocationFiresSummaryEventEndingWithTheError() {
        assertThatThrownBy(() -> otherService.chat("hello")).isInstanceOf(RuntimeException.class);

        assertThat(recorder.allEvents()).hasSize(1);
        var summary = recorder.allEvents().get(0);

        assertThat(summary.invocationContext().interfaceName()).isEqualTo(OtherAiService.class.getName());
        assertThat(summary.events()).first().isInstanceOf(AiServiceStartedEvent.class);
        assertThat(summary.events()).last().isInstanceOf(AiServiceErrorEvent.class);
    }

    @Test
    @ActivateRequestContext
    void qualifiedObserverOnlySeesItsOwnAiService() {
        assertThat(service.chat("hello")).isEqualTo("Hi!");

        assertThat(recorder.myServiceEvents()).hasSize(1);
        assertThat(recorder.otherServiceEvents()).isEmpty();
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface MyAiService {

        String chat(@UserMessage String message);
    }

    @RegisterAiService(chatLanguageModelSupplier = FailingChatModelSupplier.class)
    public interface OtherAiService {

        String chat(@UserMessage String message);
    }

    @Singleton
    public static class InvocationSummaryRecorder {

        private final List<AiServiceInvocationSummaryEvent> allEvents = new CopyOnWriteArrayList<>();
        private final List<AiServiceInvocationSummaryEvent> myServiceEvents = new CopyOnWriteArrayList<>();
        private final List<AiServiceInvocationSummaryEvent> otherServiceEvents = new CopyOnWriteArrayList<>();

        void onEvent(@Observes AiServiceInvocationSummaryEvent event) {
            allEvents.add(event);
        }

        void onMyServiceEvent(@Observes @AiServiceSelector(MyAiService.class) AiServiceInvocationSummaryEvent event) {
            myServiceEvents.add(event);
        }

        void onOtherServiceEvent(
                @Observes @AiServiceSelector(OtherAiService.class) AiServiceInvocationSummaryEvent event) {
            otherServiceEvents.add(event);
        }

        void clear() {
            allEvents.clear();
            myServiceEvents.clear();
            otherServiceEvents.clear();
        }

        List<AiServiceInvocationSummaryEvent> allEvents() {
            return allEvents;
        }

        List<AiServiceInvocationSummaryEvent> myServiceEvents() {
            return myServiceEvents;
        }

        List<AiServiceInvocationSummaryEvent> otherServiceEvents() {
            return otherServiceEvents;
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(new AiMessage("Hi!")).build();
        }
    }

    public static class FailingChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new FailingChatModel();
        }
    }

    public static class FailingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw new RuntimeException("boom");
        }
    }
}

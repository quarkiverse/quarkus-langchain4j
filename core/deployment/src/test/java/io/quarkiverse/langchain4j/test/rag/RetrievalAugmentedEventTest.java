package io.quarkiverse.langchain4j.test.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.observability.AiServiceSelector;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * LangChain4j 1.23.0 added {@link RetrievalAugmentedEvent}, which plain LangChain4j fires from
 * {@code DefaultAiServices} once per AI service invocation that uses retrieval-augmented generation.
 * The extension implements that path itself, so the event has to be fired - and bridged to CDI - here.
 */
public class RetrievalAugmentedEventTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, OtherAiService.class,
                            MyChatModel.class, MyChatModelSupplier.class,
                            MyStreamingChatModel.class, MyStreamingChatModelSupplier.class,
                            MyMemoryProviderSupplier.class, MyRetrievalAugmentor.class,
                            RetrievalAugmentedRecorder.class));

    @Inject
    MyAiService service;

    @Inject
    OtherAiService otherService;

    @Inject
    RetrievalAugmentedRecorder recorder;

    @BeforeEach
    void clearRecorder() {
        recorder.clear();
    }

    @Test
    @ActivateRequestContext
    void blockingMethodFiresRetrievalAugmentedEvent() {
        assertThat(service.blocking("1", "foo")).isEqualTo("Hi!");

        assertThat(recorder.allEvents()).hasSize(1);
        var event = recorder.allEvents().get(0);

        // the chat message handed to the retrieval augmentor, i.e. before the retrieved contents were added to it
        assertThat(event.chatMessage())
                .isInstanceOf(dev.langchain4j.data.message.UserMessage.class)
                .extracting(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText())
                .isEqualTo("foo");
        assertThat(event.contents()).containsExactly(Content.from("content1"), Content.from("content2"));
        assertThat(event.invocationContext().interfaceName()).isEqualTo(MyAiService.class.getName());
        assertThat(event.invocationContext().methodName()).isEqualTo("blocking");

        // the qualified observer only sees the events of the AI service it selects
        assertThat(recorder.myServiceEvents()).containsExactly(event);
        assertThat(recorder.otherServiceEvents()).isEmpty();
    }

    @Test
    @ActivateRequestContext
    void multiMethodFiresRetrievalAugmentedEvent() {
        var chunks = service.multi("2", "foo").collect().asList().await().indefinitely();
        assertThat(String.join(" ", chunks)).isEqualTo("Streaming hi !");

        assertThat(recorder.allEvents()).hasSize(1);
        assertThat(recorder.allEvents().get(0).contents())
                .containsExactly(Content.from("content1"), Content.from("content2"));
        assertThat(recorder.allEvents().get(0).invocationContext().methodName()).isEqualTo("multi");
    }

    @Test
    @ActivateRequestContext
    void tokenStreamMethodFiresRetrievalAugmentedEvent() throws InterruptedException {
        assertThat(collect(() -> service.tokenStream("3", "foo"))).isEqualTo("Streaming hi !");

        assertThat(recorder.allEvents()).hasSize(1);
        assertThat(recorder.allEvents().get(0).contents())
                .containsExactly(Content.from("content1"), Content.from("content2"));
        assertThat(recorder.allEvents().get(0).invocationContext().methodName()).isEqualTo("tokenStream");
    }

    @Test
    @ActivateRequestContext
    void noEventWithoutRetrievalAugmentor() {
        assertThat(otherService.blocking("4", "foo")).isEqualTo("Hi!");

        assertThat(recorder.allEvents()).isEmpty();
    }

    private static String collect(Supplier<TokenStream> invocation) throws InterruptedException {
        var latch = new CountDownLatch(1);
        var values = new ArrayList<String>();

        invocation.get()
                .onError(t -> {
                    throw new RuntimeException(t);
                })
                .onPartialResponse(values::add)
                .onCompleteResponse(response -> {
                    values.add(response.aiMessage().text());
                    latch.countDown();
                })
                .start();

        latch.await(10, TimeUnit.SECONDS);

        return String.join(" ", values).strip();
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, streamingChatLanguageModelSupplier = MyStreamingChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class, retrievalAugmentor = MyRetrievalAugmentor.class)
    public interface MyAiService {

        String blocking(@MemoryId String id, @UserMessage String message);

        Multi<String> multi(@MemoryId String id, @UserMessage String message);

        TokenStream tokenStream(@MemoryId String id, @UserMessage String message);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface OtherAiService {

        String blocking(@MemoryId String id, @UserMessage String message);
    }

    @Singleton
    public static class RetrievalAugmentedRecorder {

        private final List<RetrievalAugmentedEvent> allEvents = new CopyOnWriteArrayList<>();
        private final List<RetrievalAugmentedEvent> myServiceEvents = new CopyOnWriteArrayList<>();
        private final List<RetrievalAugmentedEvent> otherServiceEvents = new CopyOnWriteArrayList<>();

        void onEvent(@Observes RetrievalAugmentedEvent event) {
            allEvents.add(event);
        }

        void onMyServiceEvent(@Observes @AiServiceSelector(MyAiService.class) RetrievalAugmentedEvent event) {
            myServiceEvents.add(event);
        }

        void onOtherServiceEvent(@Observes @AiServiceSelector(OtherAiService.class) RetrievalAugmentedEvent event) {
            otherServiceEvents.add(event);
        }

        void clear() {
            allEvents.clear();
            myServiceEvents.clear();
            otherServiceEvents.clear();
        }

        List<RetrievalAugmentedEvent> allEvents() {
            return allEvents;
        }

        List<RetrievalAugmentedEvent> myServiceEvents() {
            return myServiceEvents;
        }

        List<RetrievalAugmentedEvent> otherServiceEvents() {
            return otherServiceEvents;
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyStreamingChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new MyStreamingChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(new AiMessage("Hi!")).build();
        }
    }

    public static class MyStreamingChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("Streaming hi");
            handler.onPartialResponse("!");
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new MessageWindowChatMemory.Builder().maxMessages(5).build();
                }
            };
        }
    }

    public static class MyRetrievalAugmentor implements Supplier<RetrievalAugmentor> {
        @Override
        public RetrievalAugmentor get() {
            return new RetrievalAugmentor() {
                @Override
                public AugmentationResult augment(AugmentationRequest augmentationRequest) {
                    ChatMessage augmented = dev.langchain4j.data.message.UserMessage.userMessage("augmented");
                    return new AugmentationResult(augmented,
                            List.of(Content.from("content1"), Content.from("content2")));
                }
            };
        }
    }
}

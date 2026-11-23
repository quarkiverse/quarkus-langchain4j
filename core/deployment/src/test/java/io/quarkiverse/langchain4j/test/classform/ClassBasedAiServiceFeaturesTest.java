package io.quarkiverse.langchain4j.test.classform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.OnThinking;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.SeedMemory;
import io.quarkiverse.langchain4j.runtime.aiservice.ThinkingEmitted;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

/**
 * Features of an AI service interface that the class form keeps: a native method inherited from a superclass, the
 * static {@code @SeedMemory} and {@code @OnThinking} methods, and a streaming method. Also, the methods a type
 * overrides with its own code are never replaced by AI methods, for a class and for an interface alike.
 */
public class ClassBasedAiServiceFeaturesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Summarizer.class, Writer.class, SeededAssistant.class, ThinkingAssistant.class,
                            StreamingAssistant.class, Template.class, FramedWriter.class, BaseChat.class,
                            DefaultingAssistant.class, RecordingModelSupplier.class, ThinkingModelSupplier.class,
                            StreamingModelSupplier.class, MemorySupplier.class));

    /** Replies with the text of the last user message and records the messages of the last request. */
    public static class RecordingModelSupplier implements Supplier<ChatModel> {

        static final List<ChatMessage> LAST_REQUEST = Collections.synchronizedList(new ArrayList<>());

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    LAST_REQUEST.clear();
                    LAST_REQUEST.addAll(request.messages());
                    var last = (dev.langchain4j.data.message.UserMessage) request.messages()
                            .get(request.messages().size() - 1);
                    return ChatResponse.builder().aiMessage(AiMessage.from(last.singleText())).build();
                }
            };
        }
    }

    /** Replies "answer" after thinking "hmm". */
    public static class ThinkingModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.builder().text("answer").thinking("hmm").build()).build();
                }
            };
        }
    }

    /** Streams "Hel" and "lo". */
    public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                    handler.onPartialResponse("Hel");
                    handler.onPartialResponse("lo");
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build());
                }
            };
        }
    }

    public static class MemorySupplier implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return MessageWindowChatMemory.withMaxMessages(10);
                }
            };
        }
    }

    public static class Summarizer {

        @UserMessage("Summarize {{text}}")
        public native String summarize(@V("text") String text);
    }

    @RegisterAiService(chatLanguageModelSupplier = RecordingModelSupplier.class)
    public static class Writer extends Summarizer {

        @UserMessage("Write about {{topic}}")
        public native String write(@V("topic") String topic);
    }

    /** A template base class: the concrete subclass implements one abstract method itself and one with the model. */
    public abstract static class Template {

        public abstract String frame(String text);

        public abstract String write(String topic);

        public String framed(String topic) {
            return frame(write(topic));
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = RecordingModelSupplier.class)
    public static class FramedWriter extends Template {

        @Override
        public String frame(String text) {
            return "[" + text + "]";
        }

        @Override
        @UserMessage("Write about {{topic}}")
        public native String write(@V("topic") String topic);
    }

    public interface BaseChat {

        String chat(String text);

        String ask(String question);
    }

    @RegisterAiService(chatLanguageModelSupplier = RecordingModelSupplier.class)
    public interface DefaultingAssistant extends BaseChat {

        @Override
        default String chat(String text) {
            return "default " + text;
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = RecordingModelSupplier.class, chatMemoryProviderSupplier = MemorySupplier.class)
    public static class SeededAssistant {

        public native String chat(@UserMessage String message);

        @SeedMemory
        static List<ChatMessage> seed() {
            return List.of(dev.langchain4j.data.message.UserMessage.from("seed question"), AiMessage.from("seed answer"));
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = ThinkingModelSupplier.class)
    public static class ThinkingAssistant {

        static final List<String> THOUGHTS = Collections.synchronizedList(new ArrayList<>());

        public native String chat(@UserMessage String message);

        @OnThinking
        static void onThinking(ThinkingEmitted event) {
            THOUGHTS.add(event.text());
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = StreamingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public static class StreamingAssistant {

        public native Multi<String> stream(@UserMessage String message);
    }

    @Inject
    Writer writer;

    @Inject
    SeededAssistant seededAssistant;

    @Inject
    ThinkingAssistant thinkingAssistant;

    @Inject
    StreamingAssistant streamingAssistant;

    @Inject
    FramedWriter framedWriter;

    @Inject
    DefaultingAssistant defaultingAssistant;

    @Test
    @ActivateRequestContext
    void aNativeMethodInheritedFromASuperclassIsAnAiServiceMethodToo() {
        assertThat(writer.write("cats")).isEqualTo("Write about cats");
        assertThat(writer.summarize("a long text")).isEqualTo("Summarize a long text");
    }

    @Test
    @ActivateRequestContext
    void aConcreteOverrideOfAnAbstractMethodOfTheSuperclassIsKept() {
        assertThat(framedWriter.frame("x")).isEqualTo("[x]");
        assertThat(framedWriter.write("cats")).isEqualTo("Write about cats");
        assertThat(framedWriter.framed("cats")).isEqualTo("[Write about cats]");
    }

    @Test
    @ActivateRequestContext
    void aDefaultOverrideOfAnAbstractMethodOfTheSuperinterfaceIsKept() {
        assertThat(defaultingAssistant.chat("x")).isEqualTo("default x");
        assertThat(defaultingAssistant.ask("why")).isEqualTo("why");
    }

    @Test
    @ActivateRequestContext
    void theSeedMemoryMethodOfTheClassSeedsTheChatMemory() {
        assertThat(seededAssistant.chat("hi")).isEqualTo("hi");
        assertThat(RecordingModelSupplier.LAST_REQUEST).hasSize(3);
        assertThat(((dev.langchain4j.data.message.UserMessage) RecordingModelSupplier.LAST_REQUEST.get(0)).singleText())
                .isEqualTo("seed question");
        assertThat(((AiMessage) RecordingModelSupplier.LAST_REQUEST.get(1)).text()).isEqualTo("seed answer");
    }

    @Test
    @ActivateRequestContext
    void theThinkingHandlerOfTheClassIsNotified() {
        assertThat(thinkingAssistant.chat("hi")).isEqualTo("answer");
        assertThat(ThinkingAssistant.THOUGHTS).containsExactly("hmm");
    }

    @Test
    @ActivateRequestContext
    void aStreamingMethodStreams() {
        List<String> tokens = streamingAssistant.stream("hi").collect().asList().await().atMost(Duration.ofSeconds(10));
        assertThat(tokens).containsExactly("Hel", "lo");
    }
}

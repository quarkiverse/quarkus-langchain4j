package org.acme.examples.aiservices;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.SeedMemory;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.test.QuarkusUnitTest;

public class ChatMemorySeederTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @BeforeEach
    void setup() {
        resetRequests();
        resetMappings();
    }

    // we define this so we can peek into the memory
    @Singleton
    public static class CustomChatMemoryStore implements ChatMemoryStore {

        // emulating persistent storage
        final Map</* memoryId */ Object, String> persistentStorage = new HashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return messagesFromJson(persistentStorage.get(memoryId));
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            persistentStorage.put(memoryId, messagesToJson(messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            persistentStorage.remove(memoryId);
        }
    }

    enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE
    }

    @RegisterAiService
    interface SentimentAnalyzer {

        @SystemMessage("You are a professional sentiment analyzer")
        @UserMessage("Analyze sentiment of: {it}")
        Sentiment analyzeSentimentOf(String text);

        @SeedMemory
        static List<ChatMessage> seed() {
            return List.of(dev.langchain4j.data.message.UserMessage.from("great!"), AiMessage.from(Sentiment.POSITIVE.name()));
        }
    }

    @Inject
    SentimentAnalyzer sentimentAnalyzer;

    @Test
    @ActivateRequestContext
    void testRequestScoped() throws IOException {
        ArcContainer container = Arc.container();
        ChatMemoryStore store = container.instance(ChatMemoryStore.class).get();
        assertThat(store).isInstanceOf(CustomChatMemoryStore.class);

        // first request
        setChatCompletionMessageContent("POSITIVE");

        Sentiment sentiment = sentimentAnalyzer
                .analyzeSentimentOf("This LaptopPro X15 is wicked fast and that 4K screen is a dream.");

        // assert response
        assertThat(sentiment).isEqualTo(Sentiment.POSITIVE);

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageContent("system", "You are a professional sentiment analyzer"),
                        new MessageContent("user", "great!"),
                        new MessageContent("assistant", "POSITIVE"),
                        new MessageContent("user",
                                "Analyze sentiment of: This LaptopPro X15 is wicked fast and that 4K screen is a dream.\nYou must answer strictly with one of these enums:\nPOSITIVE\nNEUTRAL\nNEGATIVE")));

        // assert chat memory
        Map<Object, String> memoryStorage = ((CustomChatMemoryStore) store).persistentStorage;
        assertThat(memoryStorage).hasSize(1);
        Object key = memoryStorage.keySet().iterator().next();
        List<ChatMessage> messagesFromStore = store.getMessages(key);
        assertThat(messagesFromStore).hasSize(5);

        // clear wiremock's requests, so we can assert the second request easily
        resetRequests();

        // second request
        setChatCompletionMessageContent("NEUTRAL");

        sentiment = sentimentAnalyzer.analyzeSentimentOf("The earth is round.");

        // assert response
        assertThat(sentiment).isEqualTo(Sentiment.NEUTRAL);

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageContent("system", "You are a professional sentiment analyzer"),
                        new MessageContent("user", "great!"),
                        new MessageContent("assistant", "POSITIVE"),
                        new MessageContent("user",
                                "Analyze sentiment of: This LaptopPro X15 is wicked fast and that 4K screen is a dream.\nYou must answer strictly with one of these enums:\nPOSITIVE\nNEUTRAL\nNEGATIVE"),
                        new MessageContent("assistant", "POSITIVE"),
                        new MessageContent("user",
                                "Analyze sentiment of: The earth is round.\nYou must answer strictly with one of these enums:\nPOSITIVE\nNEUTRAL\nNEGATIVE")));

        // assert memory
        messagesFromStore = store.getMessages(key);
        assertThat(messagesFromStore).hasSize(7);
    }

    @RegisterAiService
    @Singleton
    interface SingletonSentimentAnalyzer {

        @SystemMessage("You are a professional sentiment analyzer")
        @UserMessage("Analyze sentiment of: {it}")
        Sentiment analyzeSentimentOf(String text);

        @SeedMemory
        static List<ChatMessage> seed(String method) {
            if ("analyzeSentimentOf".equals(method)) {
                return List.of(dev.langchain4j.data.message.UserMessage.from("great!"),
                        AiMessage.from(Sentiment.POSITIVE.name()));
            }
            return Collections.emptyList();
        }
    }

    @Inject
    SingletonSentimentAnalyzer singletonSentimentAnalyzer;

    @Test
    void testSingleton() throws IOException {
        ArcContainer container = Arc.container();
        ChatMemoryStore store = container.instance(ChatMemoryStore.class).get();
        assertThat(store).isInstanceOf(CustomChatMemoryStore.class);

        // first request
        setChatCompletionMessageContent("POSITIVE");

        Sentiment sentiment = singletonSentimentAnalyzer
                .analyzeSentimentOf("This LaptopPro X15 is wicked fast and that 4K screen is a dream.");

        // assert response
        assertThat(sentiment).isEqualTo(Sentiment.POSITIVE);

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageContent("system", "You are a professional sentiment analyzer"),
                        new MessageContent("user", "great!"),
                        new MessageContent("assistant", "POSITIVE"),
                        new MessageContent("user",
                                "Analyze sentiment of: This LaptopPro X15 is wicked fast and that 4K screen is a dream.\nYou must answer strictly with one of these enums:\nPOSITIVE\nNEUTRAL\nNEGATIVE")));

        // assert chat memory
        Map<Object, String> memoryStorage = ((CustomChatMemoryStore) store).persistentStorage;
        assertThat(memoryStorage).hasSize(1);
        Object key = memoryStorage.keySet().iterator().next();
        List<ChatMessage> messagesFromStore = store.getMessages(key);
        assertThat(messagesFromStore).hasSize(5);

        // clear wiremock's requests, so we can assert the second request easily
        resetRequests();

        // second request
        setChatCompletionMessageContent("NEUTRAL");

        sentiment = singletonSentimentAnalyzer.analyzeSentimentOf("The earth is round.");

        // assert response
        assertThat(sentiment).isEqualTo(Sentiment.NEUTRAL);

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageContent("system", "You are a professional sentiment analyzer"),
                        new MessageContent("user", "great!"),
                        new MessageContent("assistant", "POSITIVE"),
                        new MessageContent("user",
                                "Analyze sentiment of: This LaptopPro X15 is wicked fast and that 4K screen is a dream.\nYou must answer strictly with one of these enums:\nPOSITIVE\nNEUTRAL\nNEGATIVE"),
                        new MessageContent("assistant", "POSITIVE"),
                        new MessageContent("user",
                                "Analyze sentiment of: The earth is round.\nYou must answer strictly with one of these enums:\nPOSITIVE\nNEUTRAL\nNEGATIVE")));

        // assert memory
        messagesFromStore = store.getMessages(key);
        assertThat(messagesFromStore).hasSize(7);

        // make sure we clear out all the memory in case some other test is executed
        memoryStorage.clear();
    }

}

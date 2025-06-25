package io.quarkiverse.langchain4j.memorystore.mongodb.test;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.bson.Document;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.ChatMemoryRemover;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.memorystore.MongoDBChatMemoryStore;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.runtime.LangChain4jUtil;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class MongoDBChatMemoryStoreTest extends OpenAiBaseTest {

    public static final int FIRST_MEMORY_ID = 1;
    public static final int SECOND_MEMORY_ID = 2;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @BeforeEach
    void setUp() {
        wiremock().resetRequests();
    }

    @RegisterAiService
    @ApplicationScoped
    interface ChatWithSeparateMemoryForEachUser {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Inject
    ChatMemoryStore chatMemoryStore;

    @Inject
    MongoClient mongoClient;

    @Inject
    ChatWithSeparateMemoryForEachUser chatWithSeparateMemoryForEachUser;

    @Test
    void should_keep_separate_chat_memory_for_each_user_in_store() throws IOException {
        // assert the bean type is correct
        assertThat(chatMemoryStore).isInstanceOf(MongoDBChatMemoryStore.class);

        /* **** First request for user 1 **** */
        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        setChatCompletionMessageContent("Nice to meet you Klaus");
        String firstAiResponseToFirstUser = chatWithSeparateMemoryForEachUser.chat(FIRST_MEMORY_ID, firstMessageFromFirstUser);

        // assert response
        assertThat(firstAiResponseToFirstUser).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertSingleRequestMessage(getRequestAsMap(), firstMessageFromFirstUser);

        // assert chat memory
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser));

        resetRequests();

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        setChatCompletionMessageContent("Nice to meet you Francine");
        String firstAiResponseToSecondUser = chatWithSeparateMemoryForEachUser.chat(SECOND_MEMORY_ID,
                firstMessageFromSecondUser);

        // assert response
        assertThat(firstAiResponseToSecondUser).isEqualTo("Nice to meet you Francine");

        // assert request
        assertSingleRequestMessage(getRequestAsMap(), firstMessageFromSecondUser);

        // assert chat memory
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser));

        resetRequests();

        String secondsMessageFromFirstUser = "What is my name?";
        setChatCompletionMessageContent("Your name is Klaus");
        String secondAiMessageToFirstUser = chatWithSeparateMemoryForEachUser.chat(FIRST_MEMORY_ID,
                secondsMessageFromFirstUser);

        // assert response
        assertThat(secondAiMessageToFirstUser).contains("Klaus");

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageContent("user", firstMessageFromFirstUser),
                        new MessageContent("assistant", firstAiResponseToFirstUser),
                        new MessageContent("user", secondsMessageFromFirstUser)));

        // assert chat memory
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser),
                        tuple(USER, secondsMessageFromFirstUser), tuple(AI, secondAiMessageToFirstUser));

        resetRequests();

        String secondsMessageFromSecondUser = "What is my name?";
        setChatCompletionMessageContent("Your name is Francine");
        String secondAiMessageToSecondUser = chatWithSeparateMemoryForEachUser.chat(SECOND_MEMORY_ID,
                secondsMessageFromSecondUser);

        // assert response
        assertThat(secondAiMessageToSecondUser).contains("Francine");

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageContent("user", firstMessageFromSecondUser),
                        new MessageContent("assistant", firstAiResponseToSecondUser),
                        new MessageContent("user", secondsMessageFromSecondUser)));

        // assert chat memory
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser),
                        tuple(USER, secondsMessageFromSecondUser), tuple(AI, secondAiMessageToSecondUser));

        // assert our chat memory is used
        MongoCollection<Document> collection = mongoClient.getDatabase("langchain4j").getCollection("chat_memory");
        assertThat(collection.countDocuments(Filters.in("_id", "" + FIRST_MEMORY_ID, "" + SECOND_MEMORY_ID))).isEqualTo(2);

        // remove the first entry
        ChatMemoryRemover.remove(chatWithSeparateMemoryForEachUser, FIRST_MEMORY_ID);
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).isEmpty();
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).isNotEmpty();

        // remove the second entry
        ChatMemoryRemover.remove(chatWithSeparateMemoryForEachUser, SECOND_MEMORY_ID);
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).isEmpty();
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).isEmpty();

        // now assert that our store was used for delete
        assertThat(collection.countDocuments(Filters.in("_id", "" + FIRST_MEMORY_ID, "" + SECOND_MEMORY_ID))).isEqualTo(0);
    }
}

package io.quarkiverse.langchain4j.memorystore.redis.test;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static io.quarkiverse.langchain4j.memorystore.redis.test.MessageAssertUtils.assertMultipleRequestMessage;
import static io.quarkiverse.langchain4j.memorystore.redis.test.MessageAssertUtils.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.ChatMemoryRemover;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.memorystore.RedisChatMemoryStore;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.QuarkusUnitTest;

public class RedisChatMemoryStoreTest extends WiremockAware {

    public static final int FIRST_MEMORY_ID = 1;
    public static final int SECOND_MEMORY_ID = 2;
    private static final int WIREMOCK_PORT = 8089;
    private static final String API_KEY = "test";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WiremockUtils.class, MessageAssertUtils.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    static ObjectMapper mapper;

    @BeforeAll
    static void beforeAll() {
        mapper = new ObjectMapper();
    }

    @RegisterAiService
    @ApplicationScoped
    interface ChatWithSeparateMemoryForEachUser {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Inject
    ChatMemoryStore chatMemoryStore;

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ChatWithSeparateMemoryForEachUser chatWithSeparateMemoryForEachUser;

    @Test
    void should_keep_separate_chat_memory_for_each_user_in_store() throws IOException {
        // assert the bean type is correct
        assertThat(chatMemoryStore).isInstanceOf(RedisChatMemoryStore.class);

        /* **** First request for user 1 **** */
        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        wiremock().register(WiremockUtils.chatCompletionsMessageContent(API_KEY,
                "Nice to meet you Klaus"));
        String firstAiResponseToFirstUser = chatWithSeparateMemoryForEachUser.chat(FIRST_MEMORY_ID, firstMessageFromFirstUser);

        // assert response
        assertThat(firstAiResponseToFirstUser).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertSingleRequestMessage(getRequestAsMap(), firstMessageFromFirstUser);

        // assert chat memory
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).hasSize(2)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser));

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        wiremock().register(WiremockUtils.chatCompletionsMessageContent(API_KEY,
                "Nice to meet you Francine"));
        String firstAiResponseToSecondUser = chatWithSeparateMemoryForEachUser.chat(SECOND_MEMORY_ID,
                firstMessageFromSecondUser);

        // assert response
        assertThat(firstAiResponseToSecondUser).isEqualTo("Nice to meet you Francine");

        // assert request
        assertSingleRequestMessage(getRequestAsMap(), firstMessageFromSecondUser);

        // assert chat memory
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).hasSize(2)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser));

        String secondsMessageFromFirstUser = "What is my name?";
        wiremock().register(WiremockUtils.chatCompletionsMessageContent(API_KEY,
                "Your name is Klaus"));
        String secondAiMessageToFirstUser = chatWithSeparateMemoryForEachUser.chat(FIRST_MEMORY_ID,
                secondsMessageFromFirstUser);

        // assert response
        assertThat(secondAiMessageToFirstUser).contains("Klaus");

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageAssertUtils.MessageContent("user", firstMessageFromFirstUser),
                        new MessageAssertUtils.MessageContent("assistant", firstAiResponseToFirstUser),
                        new MessageAssertUtils.MessageContent("user", secondsMessageFromFirstUser)));

        // assert chat memory
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).hasSize(4)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser),
                        tuple(USER, secondsMessageFromFirstUser), tuple(AI, secondAiMessageToFirstUser));

        String secondsMessageFromSecondUser = "What is my name?";
        wiremock().register(WiremockUtils.chatCompletionsMessageContent(API_KEY,
                "Your name is Francine"));
        String secondAiMessageToSecondUser = chatWithSeparateMemoryForEachUser.chat(SECOND_MEMORY_ID,
                secondsMessageFromSecondUser);

        // assert response
        assertThat(secondAiMessageToSecondUser).contains("Francine");

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageAssertUtils.MessageContent("user", firstMessageFromSecondUser),
                        new MessageAssertUtils.MessageContent("assistant", firstAiResponseToSecondUser),
                        new MessageAssertUtils.MessageContent("user", secondsMessageFromSecondUser)));

        // assert chat memory
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).hasSize(4)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser),
                        tuple(USER, secondsMessageFromSecondUser), tuple(AI, secondAiMessageToSecondUser));

        // assert our chat memory is used
        assertThat(redisDataSource.key().exists("" + FIRST_MEMORY_ID, "" + SECOND_MEMORY_ID)).isEqualTo(2);

        // remove the first entry
        ChatMemoryRemover.remove(chatWithSeparateMemoryForEachUser, FIRST_MEMORY_ID);
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).isEmpty();
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).isNotEmpty();

        // remove the second entry
        ChatMemoryRemover.remove(chatWithSeparateMemoryForEachUser, SECOND_MEMORY_ID);
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).isEmpty();
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).isEmpty();

        // now assert that our store was used for delete
        assertThat(redisDataSource.key().exists("" + FIRST_MEMORY_ID, "" + SECOND_MEMORY_ID)).isEqualTo(0);
    }

    private Map<String, Object> getRequestAsMap() throws IOException {
        return getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(0)));
    }

    private Map<String, Object> getRequestAsMap(byte[] body) throws IOException {
        return mapper.readValue(body, MAP_TYPE_REF);
    }

}

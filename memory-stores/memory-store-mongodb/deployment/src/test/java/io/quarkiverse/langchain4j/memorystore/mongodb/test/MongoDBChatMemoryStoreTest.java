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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
        wiremock().resetMappings();
    }

    @Override
    protected void setChatCompletionMessageContent(String messageContent) {
        wiremock().register(
                WireMock.post(WireMock.urlEqualTo("/v1/chat/completions"))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withTransformers("chat-completion-transformer")
                                .withBody("""
                                        {
                                          "id": "chatcmpl-test",
                                          "object": "chat.completion",
                                          "created": 1677652288,
                                          "model": "gpt-4o-mini",
                                          "choices": [{
                                            "index": 0,
                                            "message": {
                                              "role": "assistant",
                                              "content": "%s"
                                            },
                                            "finish_reason": "stop"
                                          }],
                                          "usage": {
                                            "prompt_tokens": 9,
                                            "completion_tokens": 12,
                                            "total_tokens": 21
                                          }
                                        }
                                        """.formatted(messageContent))));
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

    @Test
    void should_store_messages_as_bson_array_and_read_back() {
        String memoryId = "array-test";
        MongoCollection<Document> collection = mongoClient.getDatabase("langchain4j").getCollection("chat_memory");

        List<ChatMessage> messages = List.of(
                dev.langchain4j.data.message.UserMessage.from("Hello, my name is Ricardo"),
                dev.langchain4j.data.message.AiMessage.from("Nice to meet you Ricardo"));

        chatMemoryStore.updateMessages(memoryId, messages);

        // assert raw format in MongoDB: array of subdocuments, not a string
        Document raw = collection.find(Filters.eq("_id", memoryId)).first();
        assertThat(raw).isNotNull();
        assertThat(raw.get("messages")).isInstanceOf(List.class);

        List<Document> docs = raw.getList("messages", Document.class);
        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getString("type")).isEqualTo("USER");
        assertThat(docs.get(1).getString("type")).isEqualTo("AI");
        assertThat(docs.get(1).getString("text")).isEqualTo("Nice to meet you Ricardo");

        // assert getMessages rebuilds the right message types
        assertThat(chatMemoryStore.getMessages(memoryId))
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(
                        tuple(USER, "Hello, my name is Ricardo"),
                        tuple(AI, "Nice to meet you Ricardo"));

        // assert fields are queryable
        assertThat(collection.countDocuments(Filters.and(
                Filters.eq("_id", memoryId),
                Filters.eq("messages.type", "AI")))).isEqualTo(1);

        chatMemoryStore.deleteMessages(memoryId);
        assertThat(chatMemoryStore.getMessages(memoryId)).isEmpty();
    }

    @Test
    void should_read_legacy_messages_stored_as_json_string() {
        String memoryId = "legacy-test";
        MongoCollection<Document> collection = mongoClient.getDatabase("langchain4j").getCollection("chat_memory");

        String legacyJson = """
                [{"contents":[{"text":"Hello, my name is Ricardo","type":"TEXT"}],"attributes":{},"type":"USER"},\
                {"toolExecutionRequests":[],"text":"Nice to meet you Ricardo","attributes":{},"type":"AI"}]""";
        collection.insertOne(new Document("_id", memoryId).append("messages", legacyJson));

        assertThat(chatMemoryStore.getMessages(memoryId))
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(
                        tuple(USER, "Hello, my name is Ricardo"),
                        tuple(AI, "Nice to meet you Ricardo"));

        // the next update migrates the document to the array format
        chatMemoryStore.updateMessages(memoryId, chatMemoryStore.getMessages(memoryId));
        assertThat(collection.find(Filters.eq("_id", memoryId)).first().get("messages")).isInstanceOf(List.class);

        chatMemoryStore.deleteMessages(memoryId);
    }

    @Test
    void should_round_trip_all_message_types() {
        String memoryId = "all-types-test";
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("call-1")
                .name("getWeather")
                .arguments("{\"city\":\"Sao Paulo\"}")
                .build();

        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful assistant"),
                dev.langchain4j.data.message.UserMessage.from("What is the weather in Sao Paulo?"),
                AiMessage.from(toolRequest),
                ToolExecutionResultMessage.from(toolRequest, "25 degrees"),
                AiMessage.from("It is 25 degrees in Sao Paulo"));

        chatMemoryStore.updateMessages(memoryId, messages);

        assertThat(chatMemoryStore.getMessages(memoryId)).containsExactlyElementsOf(messages);

        chatMemoryStore.deleteMessages(memoryId);
    }
}

package io.quarkiverse.langchain4j.memorystore.infinispan.test;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static io.quarkiverse.langchain4j.memorystore.infinispan.test.OpenAiRequestAssertions.assertMultipleRequestMessage;
import static io.quarkiverse.langchain4j.memorystore.infinispan.test.OpenAiRequestAssertions.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
import io.quarkiverse.langchain4j.memorystore.InfinispanChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.infinispan.test.OpenAiRequestAssertions.MessageContent;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.runtime.LangChain4jUtil;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

public class InfinispanChatMemoryStoreTest {

    public static final int FIRST_MEMORY_ID = 1;
    public static final int SECOND_MEMORY_ID = 2;

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(OpenAiRequestAssertions.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");

    @Inject
    MockModelServer mock;

    @Inject
    ChatMemoryStore chatMemoryStore;

    @Inject
    RemoteCacheManager remoteCacheManager;

    @Inject
    ChatWithSeparateMemoryForEachUser chatWithSeparateMemoryForEachUser;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    private JsonNode singleChatRequestBody() {
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
        return request.bodyAsJson();
    }

    @RegisterAiService
    @ApplicationScoped
    interface ChatWithSeparateMemoryForEachUser {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Test
    void should_be_infinispan_store() {
        assertThat(chatMemoryStore).isInstanceOf(InfinispanChatMemoryStore.class);
    }

    @Test
    void should_keep_separate_chat_memory_for_each_user_in_store() {
        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        mock.chat().reply("Nice to meet you Klaus");
        String firstAiResponseToFirstUser = chatWithSeparateMemoryForEachUser.chat(FIRST_MEMORY_ID,
                firstMessageFromFirstUser);

        assertThat(firstAiResponseToFirstUser).isEqualTo("Nice to meet you Klaus");
        assertSingleRequestMessage(singleChatRequestBody(), firstMessageFromFirstUser);
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser));

        mock.reset();

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        mock.chat().reply("Nice to meet you Francine");
        String firstAiResponseToSecondUser = chatWithSeparateMemoryForEachUser.chat(SECOND_MEMORY_ID,
                firstMessageFromSecondUser);

        assertThat(firstAiResponseToSecondUser).isEqualTo("Nice to meet you Francine");
        assertSingleRequestMessage(singleChatRequestBody(), firstMessageFromSecondUser);
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser));

        mock.reset();

        String secondsMessageFromFirstUser = "What is my name?";
        mock.chat().reply("Your name is Klaus");
        String secondAiMessageToFirstUser = chatWithSeparateMemoryForEachUser.chat(FIRST_MEMORY_ID,
                secondsMessageFromFirstUser);

        assertThat(secondAiMessageToFirstUser).contains("Klaus");
        assertMultipleRequestMessage(singleChatRequestBody(),
                List.of(
                        new MessageContent("user", firstMessageFromFirstUser),
                        new MessageContent("assistant", firstAiResponseToFirstUser),
                        new MessageContent("user", secondsMessageFromFirstUser)));
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser),
                        tuple(USER, secondsMessageFromFirstUser), tuple(AI, secondAiMessageToFirstUser));

        mock.reset();

        String secondsMessageFromSecondUser = "What is my name?";
        mock.chat().reply("Your name is Francine");
        String secondAiMessageToSecondUser = chatWithSeparateMemoryForEachUser.chat(SECOND_MEMORY_ID,
                secondsMessageFromSecondUser);

        assertThat(secondAiMessageToSecondUser).contains("Francine");
        assertMultipleRequestMessage(singleChatRequestBody(),
                List.of(
                        new MessageContent("user", firstMessageFromSecondUser),
                        new MessageContent("assistant", firstAiResponseToSecondUser),
                        new MessageContent("user", secondsMessageFromSecondUser)));
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser),
                        tuple(USER, secondsMessageFromSecondUser), tuple(AI, secondAiMessageToSecondUser));

        assertThat(remoteCacheManager.getCache("chat-memory").containsKey("" + FIRST_MEMORY_ID)).isTrue();
        assertThat(remoteCacheManager.getCache("chat-memory").containsKey("" + SECOND_MEMORY_ID)).isTrue();

        ChatMemoryRemover.remove(chatWithSeparateMemoryForEachUser, FIRST_MEMORY_ID);
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).isEmpty();
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).isNotEmpty();

        ChatMemoryRemover.remove(chatWithSeparateMemoryForEachUser, SECOND_MEMORY_ID);
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).isEmpty();
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).isEmpty();

        assertThat(remoteCacheManager.getCache("chat-memory").containsKey("" + FIRST_MEMORY_ID)).isFalse();
        assertThat(remoteCacheManager.getCache("chat-memory").containsKey("" + SECOND_MEMORY_ID)).isFalse();
    }

    @Test
    void should_return_empty_for_unknown_id() {
        assertThat(chatMemoryStore.getMessages("unknown-id")).isEmpty();
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

        List<ChatMessage> retrieved = chatMemoryStore.getMessages(memoryId);
        assertThat(retrieved).hasSize(5)
                .extracting(ChatMessage::type)
                .containsExactly(SYSTEM, USER, AI, TOOL_EXECUTION_RESULT, AI);
        assertThat(retrieved).containsExactlyElementsOf(messages);

        chatMemoryStore.deleteMessages(memoryId);
    }

    @Test
    void should_persist_ai_message_thinking_field() {
        AiMessage messageWithThinking = AiMessage.builder()
                .text("The answer is 42")
                .thinking("Let me reason through this carefully. The question asks about the meaning of life...")
                .build();

        int memoryId = 999;

        chatMemoryStore.updateMessages(memoryId, List.of(messageWithThinking));

        List<ChatMessage> retrievedMessages = chatMemoryStore.getMessages(memoryId);

        assertThat(retrievedMessages).hasSize(1);
        ChatMessage retrievedMessage = retrievedMessages.get(0);
        assertThat(retrievedMessage).isInstanceOf(AiMessage.class);

        AiMessage retrievedAiMessage = (AiMessage) retrievedMessage;
        assertThat(retrievedAiMessage.text()).isEqualTo("The answer is 42");
        assertThat(retrievedAiMessage.thinking()).isEqualTo(
                "Let me reason through this carefully. The question asks about the meaning of life...");

        chatMemoryStore.deleteMessages(memoryId);
    }
}

package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static org.acme.examples.aiservices.MessageAssertUtils.assertMultipleRequestMessage;
import static org.acme.examples.aiservices.MessageAssertUtils.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.RemovableChatMemoryProvider;
import io.quarkiverse.langchain4j.openai.test.WiremockUtils;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusUnitTest;

public class RemovableChatMemoryTest {

    private static final int WIREMOCK_PORT = 8089;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WiremockUtils.class, MessageAssertUtils.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost:" + WIREMOCK_PORT + "/v1");
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    static WireMockServer wireMockServer;

    static ObjectMapper mapper;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT));
        wireMockServer.start();

        mapper = new ObjectMapper();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(WiremockUtils.defaultChatCompletionsStub());
    }

    @ApplicationScoped
    public static class ChatMemoryBean implements RemovableChatMemoryProvider {

        static final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

        @Override
        public ChatMemory get(Object memoryId) {
            return memories.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder()
                    .maxMessages(20)
                    .id(memoryId)
                    .build());
        }

        @Override
        public void remove(Object id) {
            memories.remove(id);
        }
    }

    @RegisterAiService
    interface ChatWithSeparateMemoryForEachUser {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Inject
    ChatWithSeparateMemoryForEachUser chatWithSeparateMemoryForEachUser;

    @Test
    void should_keep_separate_chat_memory_for_each_user_in_store() throws IOException {

        ManagedContext requestContext = Arc.container().requestContext();

        // add a dummy entry that should not affect the chat in any way
        ChatMemoryBean.memories.put("DUMMY", new ChatMemory() {
            @Override
            public Object id() {
                return null;
            }

            @Override
            public void add(ChatMessage message) {

            }

            @Override
            public List<ChatMessage> messages() {
                return null;
            }

            @Override
            public void clear() {

            }
        });

        try {
            requestContext.activate();
            testInRequestContext();
        } finally {
            requestContext.terminate();
        }

        // since the request context was closed, we should now only have the initial dummy entry
        assertThat(ChatMemoryBean.memories).containsOnlyKeys("DUMMY");
    }

    private void testInRequestContext() throws IOException {
        int firstMemoryId = 1;
        int secondMemoryId = 2;

        /* **** First request for user 1 **** */
        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        wireMockServer.stubFor(WiremockUtils.chatCompletionsMessageContent(Optional.empty(),
                "Nice to meet you Klaus"));
        String firstAiResponseToFirstUser = chatWithSeparateMemoryForEachUser.chat(firstMemoryId, firstMessageFromFirstUser);

        // assert response
        assertThat(firstAiResponseToFirstUser).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertSingleRequestMessage(getRequestAsMap(), firstMessageFromFirstUser);

        // assert chat memory
        assertThat(ChatMemoryBean.memories.get(firstMemoryId).messages()).hasSize(2)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser));

        /* **** First request for user 2 **** */
        wireMockServer.resetRequests();

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        wireMockServer.stubFor(WiremockUtils.chatCompletionsMessageContent(Optional.empty(),
                "Nice to meet you Francine"));
        String firstAiResponseToSecondUser = chatWithSeparateMemoryForEachUser.chat(secondMemoryId, firstMessageFromSecondUser);

        // assert response
        assertThat(firstAiResponseToSecondUser).isEqualTo("Nice to meet you Francine");

        // assert request
        assertSingleRequestMessage(getRequestAsMap(), firstMessageFromSecondUser);

        // assert chat memory
        assertThat(ChatMemoryBean.memories.get(secondMemoryId).messages()).hasSize(2)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser));

        /* **** Second request for user 1 **** */
        wireMockServer.resetRequests();

        String secondsMessageFromFirstUser = "What is my name?";
        wireMockServer.stubFor(WiremockUtils.chatCompletionsMessageContent(Optional.empty(),
                "Your name is Klaus"));
        String secondAiMessageToFirstUser = chatWithSeparateMemoryForEachUser.chat(firstMemoryId, secondsMessageFromFirstUser);

        // assert response
        assertThat(secondAiMessageToFirstUser).contains("Klaus");

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageAssertUtils.MessageContent("user", firstMessageFromFirstUser),
                        new MessageAssertUtils.MessageContent("assistant", firstAiResponseToFirstUser),
                        new MessageAssertUtils.MessageContent("user", secondsMessageFromFirstUser)));

        // assert chat memory
        assertThat(ChatMemoryBean.memories.get(firstMemoryId).messages()).hasSize(4)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser),
                        tuple(USER, secondsMessageFromFirstUser), tuple(AI, secondAiMessageToFirstUser));

        /* **** Second request for user 2 **** */
        wireMockServer.resetRequests();

        String secondsMessageFromSecondUser = "What is my name?";
        wireMockServer.stubFor(WiremockUtils.chatCompletionsMessageContent(Optional.empty(),
                "Your name is Francine"));
        String secondAiMessageToSecondUser = chatWithSeparateMemoryForEachUser.chat(secondMemoryId,
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
        assertThat(ChatMemoryBean.memories.get(secondMemoryId).messages()).hasSize(4)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser),
                        tuple(USER, secondsMessageFromSecondUser), tuple(AI, secondAiMessageToSecondUser));
    }

    private Map<String, Object> getRequestAsMap() throws IOException {
        return getRequestAsMap(getRequestBody());
    }

    private Map<String, Object> getRequestAsMap(byte[] body) throws IOException {
        return mapper.readValue(body, MAP_TYPE_REF);
    }

    private byte[] getRequestBody() {
        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);
        ServeEvent serveEvent = wireMockServer.getAllServeEvents().get(0); // this works because we reset requests for Wiremock before each test
        return getRequestBody(serveEvent);
    }

    private byte[] getRequestBody(ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        assertThat(request.getBody()).isNotEmpty();
        return request.getBody();
    }
}

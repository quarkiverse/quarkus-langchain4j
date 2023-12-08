package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static io.quarkiverse.langchain4j.openai.test.WiremockUtils.DEFAULT_TOKEN;
import static org.acme.examples.aiservices.MessageAssertUtils.assertMultipleRequestMessage;
import static org.acme.examples.aiservices.MessageAssertUtils.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.test.WiremockUtils;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class DeclarativeAiServicesTest {

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

    @RegisterAiService
    interface Assistant {

        String chat(String message);
    }

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    public void test_simple_instruction_with_single_argument_and_no_annotations() throws IOException {
        String result = assistant.chat("Tell me a joke about developers");
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), "Tell me a joke about developers");
    }

    enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE
    }

    @RegisterAiService
    interface SentimentAnalyzer {

        @UserMessage("Analyze sentiment of {it}")
        Sentiment analyzeSentimentOf(String text);
    }

    @Inject
    SentimentAnalyzer sentimentAnalyzer;

    @Test
    @ActivateRequestContext
    void test_extract_enum() throws IOException {
        wireMockServer.stubFor(WiremockUtils.chatCompletionsMessageContent(Optional.empty(), "POSITIVE"));

        Sentiment sentiment = sentimentAnalyzer
                .analyzeSentimentOf("This LaptopPro X15 is wicked fast and that 4K screen is a dream.");

        assertThat(sentiment).isEqualTo(Sentiment.POSITIVE);

        assertSingleRequestMessage(getRequestAsMap(),
                "Analyze sentiment of This LaptopPro X15 is wicked fast and that 4K screen is a dream.\nYou must answer strictly in the following format: one of [POSITIVE, NEUTRAL, NEGATIVE]");
    }

    @Singleton
    static class Calculator {

        private final Runnable after;

        Calculator(CalculatorAfter after) {
            this.after = after;
        }

        @Tool("calculates the square root of the provided number")
        double squareRoot(double number) {
            var result = Math.sqrt(number);
            after.run();
            return result;
        }
    }

    private static final String scenario = "tools";
    private static final String secondState = "second";

    @Singleton
    public static class CalculatorAfter implements Runnable {

        @Override
        public void run() {
            wireMockServer.setScenarioState(scenario, secondState);
        }
    }

    public static class ChatMemoryProviderProducer {

        @Singleton
        ChatMemoryProvider chatMemory(ChatMemoryStore store) {
            return memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(10)
                    .chatMemoryStore(store)
                    .build();
        }
    }

    @Singleton
    public static class CustomChatMemoryStore implements ChatMemoryStore {

        // emulating persistent storage
        private final Map</* memoryId */ Object, String> persistentStorage = new HashMap<>();

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

    @RegisterAiService(tools = Calculator.class, chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class)
    interface AssistantWithCalculator extends Assistant {

    }

    @Inject
    AssistantWithCalculator assistantWithCalculator;

    @Test
    @ActivateRequestContext
    void should_execute_tool_then_answer() throws IOException {
        var firstResponse = """
                {
                  "id": "chatcmpl-8D88Dag1gAKnOPP9Ed4bos7vSpaNz",
                  "object": "chat.completion",
                  "created": 1698140213,
                  "model": "gpt-3.5-turbo-0613",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "function_call": {
                          "name": "squareRoot",
                          "arguments": "{\\n  \\"number\\": 485906798473894056\\n}"
                        }
                      },
                      "finish_reason": "function_call"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 65,
                    "completion_tokens": 20,
                    "total_tokens": 85
                  }
                }
                """;

        var secondResponse = """
                        {
                          "id": "chatcmpl-8D88FIAUWSpwLaShFr0w8G1SWuVdl",
                          "object": "chat.completion",
                          "created": 1698140215,
                          "model": "gpt-3.5-turbo-0613",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8."
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 102,
                            "completion_tokens": 33,
                            "total_tokens": 135
                          }
                        }
                """;

        wireMockServer.stubFor(
                WiremockUtils.chatCompletionMapping(DEFAULT_TOKEN)
                        .inScenario(scenario)
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(WiremockUtils.CHAT_RESPONSE_WITHOUT_BODY.withBody(firstResponse)));
        wireMockServer.stubFor(
                WiremockUtils.chatCompletionMapping(DEFAULT_TOKEN)
                        .inScenario(scenario)
                        .whenScenarioStateIs(secondState)
                        .willReturn(WiremockUtils.CHAT_RESPONSE_WITHOUT_BODY.withBody(secondResponse)));

        wireMockServer.setScenarioState(scenario, Scenario.STARTED);

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        String answer = assistantWithCalculator.chat(userMessage);

        assertThat(answer).isEqualTo(
                "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.");

        assertThat(wireMockServer.getAllServeEvents()).hasSize(2);

        assertSingleRequestMessage(getRequestAsMap(getRequestBody(wireMockServer.getAllServeEvents().get(1))),
                "What is the square root of 485906798473894056 in scientific notation?");
        assertMultipleRequestMessage(getRequestAsMap(getRequestBody(wireMockServer.getAllServeEvents().get(0))),
                List.of(
                        new MessageAssertUtils.MessageContent("user",
                                "What is the square root of 485906798473894056 in scientific notation?"),
                        new MessageAssertUtils.MessageContent("assistant", null),
                        new MessageAssertUtils.MessageContent("function", "6.97070153193991E8")));
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class)
    interface ChatWithSeparateMemoryForEachUser {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Inject
    ChatWithSeparateMemoryForEachUser chatWithSeparateMemoryForEachUser;

    @Test
    @ActivateRequestContext
    void should_keep_separate_chat_memory_for_each_user_in_store() throws IOException {

        ChatMemoryStore store = Arc.container().instance(ChatMemoryStore.class).get();

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
        assertThat(store.getMessages(firstMemoryId)).hasSize(2)
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
        assertThat(store.getMessages(secondMemoryId)).hasSize(2)
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
        assertThat(store.getMessages(firstMemoryId)).hasSize(4)
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
        assertThat(store.getMessages(secondMemoryId)).hasSize(4)
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

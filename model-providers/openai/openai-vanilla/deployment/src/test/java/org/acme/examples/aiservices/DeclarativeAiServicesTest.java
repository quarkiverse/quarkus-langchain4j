package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.ImageUrl;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class DeclarativeAiServicesTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @BeforeEach
    void setup() {
        resetRequests();
        resetMappings();
    }

    interface AssistantBase {

        String chat(String message);
    }

    @RegisterAiService
    interface Assistant extends AssistantBase {

        String chat2(String message);
    }

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    public void test_simple_instruction_with_single_argument_and_no_annotations_from_super() throws IOException {
        String result = assistant.chat("Tell me a joke about developers");
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), "Tell me a joke about developers");
    }

    @Test
    @ActivateRequestContext
    public void test_simple_instruction_with_single_argument_and_no_annotations_from_iface() throws IOException {
        String result = assistant.chat2("Tell me a joke about developers");
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), "Tell me a joke about developers");
    }

    @Singleton
    public static class DummyRetriever implements Retriever<TextSegment> {

        @Override
        public List<TextSegment> findRelevant(String text) {
            return List.of(TextSegment.from("dummy"));
        }
    }

    @RegisterAiService(retriever = DummyRetriever.class)
    interface AssistantWithRetriever {

        String chat(String message);
    }

    @Inject
    AssistantWithRetriever assistantWithRetriever;

    @Test
    @ActivateRequestContext
    public void test_simple_instruction_with_retriever() throws IOException {
        String result = assistantWithRetriever.chat("Tell me a joke about developers");
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(),
                "Tell me a joke about developers\n\nAnswer using the following information:\ndummy");
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
        setChatCompletionMessageContent("POSITIVE");

        Sentiment sentiment = sentimentAnalyzer
                .analyzeSentimentOf("This LaptopPro X15 is wicked fast and that 4K screen is a dream.");

        assertThat(sentiment).isEqualTo(Sentiment.POSITIVE);

        assertSingleRequestMessage(getRequestAsMap(),
                "Analyze sentiment of This LaptopPro X15 is wicked fast and that 4K screen is a dream.\nYou must answer strictly with one of these enums:\nPOSITIVE\nNEUTRAL\nNEGATIVE");
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

        private final Integer wiremockPort;

        public CalculatorAfter(@ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
            this.wiremockPort = wiremockPort;
        }

        @Override
        public void run() {
            WireMock wireMock = new WireMock(wiremockPort);
            wireMock.setSingleScenarioState(scenario, secondState);
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

    @RegisterAiService(tools = Calculator.class)
    interface AssistantWithCalculator extends AssistantBase {

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

        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer whatever"))
                        .inScenario(scenario)
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(firstResponse)));
        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer whatever"))
                        .inScenario(scenario)
                        .whenScenarioStateIs(secondState)
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(secondResponse)));

        wiremock().setSingleScenarioState(scenario, Scenario.STARTED);

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        String answer = assistantWithCalculator.chat(userMessage);

        assertThat(answer).isEqualTo(
                "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.");

        assertThat(wiremock().getServeEvents()).hasSize(2);

        assertSingleRequestMessage(getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(1))),
                "What is the square root of 485906798473894056 in scientific notation?");
        assertMultipleRequestMessage(getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(0))),
                List.of(
                        new MessageContent("user",
                                "What is the square root of 485906798473894056 in scientific notation?"),
                        new MessageContent("assistant", null),
                        new MessageContent("function", "6.97070153193991E8")));
    }

    @RegisterAiService
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
        setChatCompletionMessageContent("Nice to meet you Klaus");
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
        resetRequests();

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        setChatCompletionMessageContent("Nice to meet you Francine");
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
        resetRequests();

        String secondsMessageFromFirstUser = "What is my name?";
        setChatCompletionMessageContent("Your name is Klaus");
        String secondAiMessageToFirstUser = chatWithSeparateMemoryForEachUser.chat(firstMemoryId, secondsMessageFromFirstUser);

        // assert response
        assertThat(secondAiMessageToFirstUser).contains("Klaus");

        // assert request
        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageContent("user", firstMessageFromFirstUser),
                        new MessageContent("assistant", firstAiResponseToFirstUser),
                        new MessageContent("user", secondsMessageFromFirstUser)));

        // assert chat memory
        assertThat(store.getMessages(firstMemoryId)).hasSize(4)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser),
                        tuple(USER, secondsMessageFromFirstUser), tuple(AI, secondAiMessageToFirstUser));

        /* **** Second request for user 2 **** */
        resetRequests();

        String secondsMessageFromSecondUser = "What is my name?";
        setChatCompletionMessageContent("Your name is Francine");
        String secondAiMessageToSecondUser = chatWithSeparateMemoryForEachUser.chat(secondMemoryId,
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
        assertThat(store.getMessages(secondMemoryId)).hasSize(4)
                .extracting(ChatMessage::type, ChatMessage::text)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser),
                        tuple(USER, secondsMessageFromSecondUser), tuple(AI, secondAiMessageToSecondUser));
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface NoMemoryService {

        String chat(@UserMessage String userMessage);
    }

    @Inject
    NoMemoryService noMemoryService;

    @Test
    @ActivateRequestContext
    void no_memory_should_be_used() throws IOException {

        String firstUserMessage = "Hello, my name is Klaus";
        setChatCompletionMessageContent("Nice to meet you Klaus");
        String firstAiResponse = noMemoryService.chat(firstUserMessage);

        // assert response
        assertThat(firstAiResponse).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertSingleRequestMessage(getRequestAsMap(), firstUserMessage);

        resetRequests();

        String secondUserMessage = "What is my name";
        setChatCompletionMessageContent("I don't know");
        String secondAiResponse = noMemoryService.chat(secondUserMessage);

        // assert response
        assertThat(secondAiResponse).isEqualTo("I don't know");

        // assert request only contains the second request, so no memory is used
        assertSingleRequestMessage(getRequestAsMap(), secondUserMessage);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @ApplicationScoped
    interface ImageDescriber {

        @UserMessage("This is image was reported on a GitHub issue. If this is a snippet of Java code, please respond"
                + " with only the {language} code. If it is not, respond with '{notImageResponse}'")
        String describe(String language, @ImageUrl String url, String notImageResponse);
    }

    @Inject
    ImageDescriber imageDescriber;

    @Test
    public void test_image_describer() throws IOException {
        wiremock().register(post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4o-mini")))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "chatcmpl-123",
                                  "object": "chat.completion",
                                  "created": 1677652288,
                                  "model": "gpt-4o-mini",
                                  "system_fingerprint": "fp_44709d6fcb",
                                  "choices": [{
                                    "index": 0,
                                    "message": {
                                      "role": "assistant",
                                      "content": "https://image.io"
                                    },
                                    "logprobs": null,
                                    "finish_reason": "stop"
                                  }],
                                  "usage": {
                                    "prompt_tokens": 9,
                                    "completion_tokens": 12,
                                    "total_tokens": 21,
                                    "completion_tokens_details": {
                                      "reasoning_tokens": 0
                                    }
                                  }
                                }
                                """)));

        String imageUrl = "https://foo.bar";
        String response = imageDescriber.describe("Java", imageUrl, "NOT_AN_IMAGE");

        // assert response
        assertThat(response).isEqualTo("https://image.io");

        // assert request
        Map<String, Object> requestAsMap = getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(0)));
        assertMessages(requestAsMap, new Consumer<>() {
            @Override
            public void accept(List<? extends Map> maps) {
                assertThat(maps).singleElement().satisfies((Consumer<Map>) map -> {
                    assertThat(map).containsEntry("role", "user").containsKey("content");
                    assertThat(map.get("content")).isInstanceOfSatisfying(List.class, contents -> {
                        assertThat(contents).hasSize(2);
                        assertThat(contents.get(0)).isInstanceOfSatisfying(Map.class, content -> {
                            assertThat(content).containsEntry("type", "text").containsEntry("text",
                                    "This is image was reported on a GitHub issue. If this is a snippet of Java code, please respond with only the Java code. If it is not, respond with 'NOT_AN_IMAGE'");
                        });
                        assertThat(contents.get(1)).isInstanceOfSatisfying(Map.class, content -> {
                            assertThat(content).containsEntry("type", "image_url");
                            assertThat(content.get("image_url")).isInstanceOfSatisfying(Map.class,
                                    imageUrlMap -> {
                                        assertThat(imageUrlMap).containsEntry("url", "https://foo.bar");
                                    });
                        });
                    });
                });
            }
        });
    }
}

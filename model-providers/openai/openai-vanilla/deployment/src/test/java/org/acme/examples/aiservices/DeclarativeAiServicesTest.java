package org.acme.examples.aiservices;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertMultipleRequestMessage;
import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.acme.examples.aiservices.OpenAiRequestAssertions.MessageContent;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.ImageUrl;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.runtime.LangChain4jUtil;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class DeclarativeAiServicesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(OpenAiRequestAssertions.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");

    @BeforeEach
    void setup() {
        mock.reset();
    }

    interface AssistantBase {

        String chat(String message);
    }

    @RegisterAiService
    interface Assistant extends AssistantBase {

        String chat2(String message);
    }

    @Inject
    MockModelServer mock;

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    public void test_simple_instruction_with_single_argument_and_no_annotations_from_super() {
        String result = assistant.chat("Tell me a joke about developers");
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), "Tell me a joke about developers");
    }

    @Test
    @ActivateRequestContext
    public void test_simple_instruction_with_single_argument_and_no_annotations_from_iface() {
        String result = assistant.chat2("Tell me a joke about developers");
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), "Tell me a joke about developers");
    }

    public static class DummyRetriever implements RetrievalAugmentor {

        @Override
        public AugmentationResult augment(AugmentationRequest augmentationRequest) {
            ChatMessage chatMessage = augmentationRequest.chatMessage();
            if (chatMessage instanceof dev.langchain4j.data.message.UserMessage userMessage) {
                Content content = userMessage.contents().get(0);
                if (content instanceof TextContent textContent) {
                    return new AugmentationResult(new dev.langchain4j.data.message.UserMessage(
                            textContent.text() + "\n\nAnswer using the following information:\ndummy"),
                            Collections.emptyList());
                }

            }
            return new AugmentationResult(augmentationRequest.chatMessage(), Collections.emptyList());
        }

        public static class Supplier implements java.util.function.Supplier<RetrievalAugmentor> {

            @Override
            public RetrievalAugmentor get() {
                return new DummyRetriever();
            }
        }
    }

    @RegisterAiService(retrievalAugmentor = DummyRetriever.Supplier.class)
    interface AssistantWithRetriever {

        String chat(String message);
    }

    @Inject
    AssistantWithRetriever assistantWithRetriever;

    @Test
    @ActivateRequestContext
    public void test_simple_instruction_with_retriever() {
        String result = assistantWithRetriever.chat("Tell me a joke about developers");
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
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
    void test_extract_enum() {
        mock.chat().reply("POSITIVE");

        Sentiment sentiment = sentimentAnalyzer
                .analyzeSentimentOf("This LaptopPro X15 is wicked fast and that 4K screen is a dream.");

        assertThat(sentiment).isEqualTo(Sentiment.POSITIVE);

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                "Analyze sentiment of This LaptopPro X15 is wicked fast and that 4K screen is a dream.\nYou must answer strictly with one of these enums:\nPOSITIVE\nNEUTRAL\nNEGATIVE");
    }

    @Singleton
    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(double number) {
            return Math.sqrt(number);
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
    void should_execute_tool_then_answer() {
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

        // the fixtures use the legacy function_call format, which the neutral model cannot express
        mock.raw().on("POST", "/v1/chat/completions").replies(
                RawResponse.json(200, firstResponse),
                RawResponse.json(200, secondResponse));

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        String answer = assistantWithCalculator.chat(userMessage);

        assertThat(answer).isEqualTo(
                "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.");

        List<CapturedRequest> requests = mock.requests();
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
        });

        // the second request carries the tool result
        assertSingleRequestMessage(requests.get(0).bodyAsJson(),
                "What is the square root of 485906798473894056 in scientific notation?");
        assertMultipleRequestMessage(requests.get(1).bodyAsJson(),
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
    void should_keep_separate_chat_memory_for_each_user_in_store() {

        ChatMemoryStore store = Arc.container().instance(ChatMemoryStore.class).get();

        int firstMemoryId = 1;
        int secondMemoryId = 2;

        /* **** First request for user 1 **** */
        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        mock.chat().reply("Nice to meet you Klaus");
        String firstAiResponseToFirstUser = chatWithSeparateMemoryForEachUser.chat(firstMemoryId, firstMessageFromFirstUser);

        // assert response
        assertThat(firstAiResponseToFirstUser).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), firstMessageFromFirstUser);

        // assert chat memory
        assertThat(store.getMessages(firstMemoryId)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser));

        /* **** First request for user 2 **** */
        mock.reset();

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        mock.chat().reply("Nice to meet you Francine");
        String firstAiResponseToSecondUser = chatWithSeparateMemoryForEachUser.chat(secondMemoryId, firstMessageFromSecondUser);

        // assert response
        assertThat(firstAiResponseToSecondUser).isEqualTo("Nice to meet you Francine");

        // assert request
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), firstMessageFromSecondUser);

        // assert chat memory
        assertThat(store.getMessages(secondMemoryId)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser));

        /* **** Second request for user 1 **** */
        mock.reset();

        String secondsMessageFromFirstUser = "What is my name?";
        mock.chat().reply("Your name is Klaus");
        String secondAiMessageToFirstUser = chatWithSeparateMemoryForEachUser.chat(firstMemoryId, secondsMessageFromFirstUser);

        // assert response
        assertThat(secondAiMessageToFirstUser).contains("Klaus");

        // assert request
        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("user", firstMessageFromFirstUser),
                        new MessageContent("assistant", firstAiResponseToFirstUser),
                        new MessageContent("user", secondsMessageFromFirstUser)));

        // assert chat memory
        assertThat(store.getMessages(firstMemoryId)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser),
                        tuple(USER, secondsMessageFromFirstUser), tuple(AI, secondAiMessageToFirstUser));

        /* **** Second request for user 2 **** */
        mock.reset();

        String secondsMessageFromSecondUser = "What is my name?";
        mock.chat().reply("Your name is Francine");
        String secondAiMessageToSecondUser = chatWithSeparateMemoryForEachUser.chat(secondMemoryId,
                secondsMessageFromSecondUser);

        // assert response
        assertThat(secondAiMessageToSecondUser).contains("Francine");

        // assert request
        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("user", firstMessageFromSecondUser),
                        new MessageContent("assistant", firstAiResponseToSecondUser),
                        new MessageContent("user", secondsMessageFromSecondUser)));

        // assert chat memory
        assertThat(store.getMessages(secondMemoryId)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
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
    void no_memory_should_be_used() {

        String firstUserMessage = "Hello, my name is Klaus";
        mock.chat().reply("Nice to meet you Klaus");
        String firstAiResponse = noMemoryService.chat(firstUserMessage);

        // assert response
        assertThat(firstAiResponse).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), firstUserMessage);

        mock.reset();

        String secondUserMessage = "What is my name";
        mock.chat().reply("I don't know");
        String secondAiResponse = noMemoryService.chat(secondUserMessage);

        // assert response
        assertThat(secondAiResponse).isEqualTo("I don't know");

        // assert request only contains the second request, so no memory is used
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), secondUserMessage);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @ApplicationScoped
    interface ImageDescriber {

        Image generate(String input);

        @UserMessage("This is image was reported on a GitHub issue. If this is a snippet of Java code, please respond"
                + " with only the {language} code. If it is not, respond with '{notImageResponse}'")
        String describe(String language, @ImageUrl String url, String notImageResponse);

        @UserMessage("This is image was reported on a GitHub issue. If this is a snippet of Java code, please respond"
                + " with only the {language} code. If it is not, respond with '{notImageResponse}'")
        String describe2(String language, Image image, String notImageResponse);
    }

    @Inject
    ImageDescriber imageDescriber;

    @Test
    public void test_image_describer() throws Exception {
        var imageUrl = "https://foo.bar";
        doTestImageDescriber(() -> imageDescriber.describe("Java", imageUrl, "NOT_AN_IMAGE"));
        doTestImageDescriber(() -> imageDescriber.describe2("Java", Image.builder().url(imageUrl).build(), "NOT_AN_IMAGE"));

        // make sure the class is properly generated - see https://github.com/quarkiverse/quarkus-langchain4j/issues/954
        ImageDescriber unwrapped = ClientProxy.unwrap(imageDescriber);
        unwrapped.getClass().getConstructor(QuarkusAiServiceContext.class).getAnnotation(Inject.class);
    }

    private void doTestImageDescriber(Supplier<String> describerSupplier) {
        mock.reset();
        mock.chat().reply(ChatReply.text("https://image.io").usage(9, 12));

        String response = describerSupplier.get();

        // assert response
        assertThat(response).isEqualTo("https://image.io");

        // assert request
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
        JsonNode requestBody = request.bodyAsJson();
        assertThat(requestBody.path("model").textValue()).isEqualTo("gpt-6-luna");
        assertThat(requestBody.get("messages")).singleElement().satisfies(message -> {
            assertThat(message.path("role").textValue()).isEqualTo("user");
            JsonNode contents = message.get("content");
            assertThat(contents.isArray()).isTrue();
            assertThat(contents).hasSize(2);
            assertThat(contents.get(0).path("type").textValue()).isEqualTo("text");
            assertThat(contents.get(0).path("text").textValue()).isEqualTo(
                    "This is image was reported on a GitHub issue. If this is a snippet of Java code, please respond with only the Java code. If it is not, respond with 'NOT_AN_IMAGE'");
            assertThat(contents.get(1).path("type").textValue()).isEqualTo("image_url");
            assertThat(contents.get(1).path("image_url").path("url").textValue()).isEqualTo("https://foo.bar");
        });
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @ApplicationScoped
    interface MultiImageDescriber {

        @UserMessage("Describe all {count} images")
        String describeAll(int count, List<Image> images);
    }

    @Inject
    MultiImageDescriber multiImageDescriber;

    @Test
    public void test_multi_image_describer() throws Exception {
        mock.chat().reply(ChatReply.text("Two images described").usage(9, 12));

        var images = List.of(
                Image.builder().url("https://example.com/img1.png").build(),
                Image.builder().url("https://example.com/img2.png").build());

        String response = multiImageDescriber.describeAll(2, images);

        assertThat(response).isEqualTo("Two images described");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
        JsonNode requestBody = request.bodyAsJson();
        assertThat(requestBody.path("model").textValue()).isEqualTo("gpt-6-luna");
        assertThat(requestBody.get("messages")).singleElement().satisfies(message -> {
            assertThat(message.path("role").textValue()).isEqualTo("user");
            JsonNode contents = message.get("content");
            assertThat(contents.isArray()).isTrue();
            // text + 2 images = 3 content parts
            assertThat(contents).hasSize(3);
            assertThat(contents.get(0).path("type").textValue()).isEqualTo("text");
            assertThat(contents.get(0).path("text").textValue()).isEqualTo("Describe all 2 images");
            assertThat(contents.get(1).path("type").textValue()).isEqualTo("image_url");
            assertThat(contents.get(1).path("image_url").path("url").textValue())
                    .isEqualTo("https://example.com/img1.png");
            assertThat(contents.get(2).path("type").textValue()).isEqualTo("image_url");
            assertThat(contents.get(2).path("image_url").path("url").textValue())
                    .isEqualTo("https://example.com/img2.png");
        });
    }

    @RegisterAiService
    @Named("namedAssistant")
    @ApplicationScoped
    interface NamedAssistant extends AssistantBase {

        String chat(String message);
    }

    @Test
    public void test_named_assistant() throws Exception {
        Object namedAssistant = Arc.container().instance("namedAssistant").get();
        assertThat(namedAssistant).isInstanceOf(NamedAssistant.class);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ TYPE, METHOD })
    @Inherited
    @InterceptorBinding
    public @interface Canned {
    }

    @Canned
    @Interceptor
    @Priority(Interceptor.Priority.PLATFORM_AFTER + 10)
    public static class CannedInterceptor {

        @AroundInvoke
        Object intercept(InvocationContext context) {
            return "canned";
        }

    }

    @RegisterAiService
    interface CannedAssistant {

        @Canned
        String chat(String message);
    }

    @Inject
    CannedAssistant cannedAssistant;

    @Test
    @ActivateRequestContext
    public void test_custom_cdi_interceptor() {
        String result = cannedAssistant.chat("Tell me a joke about developers");
        assertThat(result).isEqualTo("canned");

        assertThat(mock.requests()).isEmpty();
    }

    @RegisterAiService
    public interface HasChatMemoryAccess extends ChatMemoryAccess {
        String chat(@MemoryId Integer memoryId, @UserMessage String text);
    }

    @Inject
    HasChatMemoryAccess hasChatMemoryAccess;

    @Test
    @ActivateRequestContext
    public void test_chat_memory_access() {
        int firstMemoryId = 111;
        int secondMemoryId = 222;

        assertThat(hasChatMemoryAccess.getChatMemory(firstMemoryId)).isNull();
        assertThat(hasChatMemoryAccess.getChatMemory(secondMemoryId)).isNull();

        /* **** First request for user 1 **** */
        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        mock.chat().reply("Nice to meet you Klaus");
        String firstAiResponseToFirstUser = hasChatMemoryAccess.chat(firstMemoryId, firstMessageFromFirstUser);

        // assert response
        assertThat(firstAiResponseToFirstUser).isEqualTo("Nice to meet you Klaus");

        // assert getChatMemory
        assertThat(hasChatMemoryAccess.getChatMemory(firstMemoryId)).isNotNull();
        assertThat(hasChatMemoryAccess.getChatMemory(firstMemoryId).messages()).hasSize(2);

        /* **** First request for user 2 **** */
        mock.reset();

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        mock.chat().reply("Nice to meet you Francine");
        String firstAiResponseToSecondUser = hasChatMemoryAccess.chat(secondMemoryId, firstMessageFromSecondUser);

        // assert response
        assertThat(firstAiResponseToSecondUser).isEqualTo("Nice to meet you Francine");

        // assert getChatMemory
        assertThat(hasChatMemoryAccess.getChatMemory(secondMemoryId)).isNotNull();
        assertThat(hasChatMemoryAccess.getChatMemory(secondMemoryId).messages()).hasSize(2);

        /* **** Second request for user 1 **** */
        mock.reset();

        String secondsMessageFromFirstUser = "What is my name?";
        mock.chat().reply("Your name is Klaus");
        String secondAiMessageToFirstUser = hasChatMemoryAccess.chat(firstMemoryId, secondsMessageFromFirstUser);

        // assert response
        assertThat(secondAiMessageToFirstUser).contains("Klaus");

        // assert getChatMemory
        assertThat(hasChatMemoryAccess.getChatMemory(firstMemoryId)).isNotNull();
        assertThat(hasChatMemoryAccess.getChatMemory(firstMemoryId).messages()).hasSize(4);

        // evict memory
        hasChatMemoryAccess.evictChatMemory(secondMemoryId);

        // assert memory state after eviction
        assertThat(hasChatMemoryAccess.getChatMemory(secondMemoryId)).isNull();
        assertThat(hasChatMemoryAccess.getChatMemory(firstMemoryId)).isNotNull();
        assertThat(hasChatMemoryAccess.getChatMemory(firstMemoryId).messages()).hasSize(4);
    }
}

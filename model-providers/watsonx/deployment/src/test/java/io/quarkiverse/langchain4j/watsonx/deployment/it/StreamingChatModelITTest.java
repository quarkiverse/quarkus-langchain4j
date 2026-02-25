package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.core.Json;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class StreamingChatModelITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "30s")

            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"wrong-key\".api-key", "wrong-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"think-model\".chat-model.model-name",
                    "ibm/granite-3-3-8b-instruct")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"think-model\".chat-model.thinking.tags.think", "think")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"think-model\".chat-model.thinking.tags.response",
                    "response")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    StreamingChatModel chatModel;

    @Inject
    @ModelName("wrong-key")
    StreamingChatModel wrongKeyChatModel;

    @Inject
    @ModelName("think-model")
    StreamingChatModel thinkingChatModel;

    @Test
    void test_chat() {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<>();
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        chatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatResponse.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                throwable.set(error);
            }
        });

        assertDoesNotThrow(() -> latch.await(30, TimeUnit.SECONDS));
        assertNotNull(throwable);
        var text = chatResponse.get().aiMessage().text();

        assertNotNull(chatResponse);
        assertNotNull(text);
        assertFalse(text.isBlank());

        assertNotNull(chatResponse.get().finishReason());
    }

    @Test
    void test_chat_json() {

        record Poem(String content, String topic) {
        }

        var parameters = ChatRequestParameters.builder()
                .temperature(0.0)
                .responseFormat(ResponseFormat.JSON)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("""
                        Create a poem about dog, max 3 lines
                        Answer using the following json structure:
                        {
                            "content": <poem content>
                            "topic": <poem topic>
                        }"""))
                .parameters(parameters)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<>();
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        chatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatResponse.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                throwable.set(error);
            }
        });

        assertDoesNotThrow(() -> latch.await(30, TimeUnit.SECONDS));
        assertNotNull(throwable);
        var poem = Json.fromJson(chatResponse.get().aiMessage().text(), Poem.class);

        assertNotNull(chatResponse);
        assertNotNull(poem);
        assertFalse(poem.content().isBlank());
        assertTrue(poem.topic.equalsIgnoreCase("dog"));
    }

    @Test
    void test_chat_json_schema() {

        record Poem(String content, String topic) {
        }

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("Animal")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("content")
                                .addEnumProperty("topic", List.of("dog", "cat"))
                                .required("content", "topic")
                                .build())
                        .build())
                .build();

        var parameters = ChatRequestParameters.builder()
                .temperature(0.0)
                .modelName("mistralai/mistral-small-3-1-24b-instruct-2503")
                .responseFormat(responseFormat)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Create a poem about dog, max 3 lines"))
                .parameters(parameters)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<>();
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        chatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatResponse.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                throwable.set(error);
            }
        });

        assertDoesNotThrow(() -> latch.await(30, TimeUnit.SECONDS));
        assertNotNull(throwable);
        var poem = Json.fromJson(chatResponse.get().aiMessage().text(), Poem.class);

        assertNotNull(chatResponse);
        assertNotNull(poem);
        assertFalse(poem.content().isBlank());
        assertTrue(poem.topic.equalsIgnoreCase("dog"));
    }

    @Test
    void test_chat_thinking() {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Why the sky is blue?"))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<>();
        StringBuilder thinkingBuilder = new StringBuilder();
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        thinkingChatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatResponse.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                throwable.set(error);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                thinkingBuilder.append(partialThinking.text());
            }
        });

        assertDoesNotThrow(() -> latch.await(30, TimeUnit.SECONDS));
        assertNotNull(throwable);

        var text = chatResponse.get().aiMessage().text();

        assertNotNull(chatResponse);
        assertNotNull(text);
        assertFalse(text.isBlank());
        assertFalse(text.contains("<think>") && text.contains("</think>"));
        assertFalse(text.contains("<response>") && text.contains("</response>"));

        var thinkingMessage = chatResponse.get().aiMessage().thinking();
        assertNotNull(thinkingMessage);
        assertFalse(thinkingMessage.isBlank());

        assertNotNull(thinkingBuilder.toString());
        assertEquals(thinkingMessage, thinkingBuilder.toString());
    }

    @Test
    void test_chat_thinking_with_parameters() {

        WatsonxChatRequestParameters parameters = WatsonxChatRequestParameters.builder()
                .modelName("ibm/granite-3-3-8b-instruct")
                .thinking(new ExtractionTags("think", "response"))
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Why the sky is blue?"))
                .parameters(parameters)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<>();
        StringBuilder thinkingBuilder = new StringBuilder();
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        thinkingChatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatResponse.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                throwable.set(error);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                thinkingBuilder.append(partialThinking.text());
            }
        });

        assertDoesNotThrow(() -> latch.await(30, TimeUnit.SECONDS));
        assertNotNull(throwable);

        var text = chatResponse.get().aiMessage().text();

        assertNotNull(chatResponse);
        assertNotNull(text);
        assertFalse(text.isBlank());
        assertFalse(text.contains("<think>") && text.contains("</think>"));
        assertFalse(text.contains("<response>") && text.contains("</response>"));

        var thinkingMessage = chatResponse.get().aiMessage().thinking();
        assertNotNull(thinkingMessage);
        assertFalse(thinkingMessage.isBlank());

        assertNotNull(thinkingBuilder.toString());
        assertEquals(thinkingMessage, thinkingBuilder.toString());
    }

    @Test
    void test_chat_tool_without_params() {

        ChatRequest chatRequest = ChatRequest.builder()
                .modelName("mistralai/mistral-small-3-1-24b-instruct-2503")
                .messages(UserMessage.from("What time is it?"))
                .toolSpecifications(ToolSpecification.builder()
                        .name("get_time")
                        .description("Get the current time")
                        .build())
                .build();

        CountDownLatch latchCompleteResponse = new CountDownLatch(1);
        CountDownLatch latchCompleteToolCall = new CountDownLatch(1);
        CountDownLatch latchPartialToolCall = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<>();
        AtomicReference<CompleteToolCall> toolCall = new AtomicReference<>();
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        chatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatResponse.set(completeResponse);
                latchCompleteResponse.countDown();
            }

            @Override
            public void onError(Throwable error) {
                throwable.set(error);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                toolCall.set(completeToolCall);
                latchCompleteToolCall.countDown();
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                latchPartialToolCall.countDown();
            }
        });

        assertDoesNotThrow(() -> latchCompleteResponse.await(30, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> latchCompleteToolCall.await(3, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> latchPartialToolCall.await(3, TimeUnit.SECONDS));
        assertNotNull(throwable);
        assertNotNull(chatResponse);
        assertNotNull(toolCall.get());

        var tools = chatResponse.get().aiMessage().toolExecutionRequests();
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertNotNull(tools.get(0).id());
        assertEquals("get_time", tools.get(0).name());
        assertEquals("{}", tools.get(0).arguments());
    }

    @Test
    void test_chat_tool_choice_option() {

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .toolChoice(ToolChoice.REQUIRED)
                .toolSpecifications(
                        ToolSpecification.builder()
                                .name("send_email")
                                .description("Send an email")
                                .parameters(JsonObjectSchema.builder()
                                        .addStringProperty("to")
                                        .addStringProperty("subject")
                                        .addStringProperty("body")
                                        .required("to", "body")
                                        .build())
                                .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello!"))
                .parameters(parameters)
                .build();

        CountDownLatch latchCompleteResponse = new CountDownLatch(1);
        CountDownLatch latchCompleteToolCall = new CountDownLatch(1);
        CountDownLatch latchPartialToolCall = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<>();
        AtomicReference<CompleteToolCall> toolCall = new AtomicReference<>();
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        chatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatResponse.set(completeResponse);
                latchCompleteResponse.countDown();
            }

            @Override
            public void onError(Throwable error) {
                throwable.set(error);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                toolCall.set(completeToolCall);
                latchCompleteToolCall.countDown();
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                latchPartialToolCall.countDown();
            }
        });

        assertDoesNotThrow(() -> latchCompleteResponse.await(300, TimeUnit.SECONDS));
        assertNotNull(toolCall.get());

        var assistantMessage = chatResponse.get().aiMessage();
        assertTrue(assistantMessage.text() == null || assistantMessage.text().isBlank());
        assertNotNull(assistantMessage.toolExecutionRequests());
        assertEquals(1, assistantMessage.toolExecutionRequests().size());
    }

    @Test
    void test_chat_with_invalid_api_key() {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello!"))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        wrongKeyChatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
            }

            @Override
            public void onError(Throwable error) {
                throwable.set(error);
                latch.countDown();
            }
        });

        assertDoesNotThrow(() -> latch.await(3, TimeUnit.SECONDS));
        assertTrue(throwable.get().getMessage().contains("Provided API key could not be found."));
    }
}

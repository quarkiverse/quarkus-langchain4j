package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Response;
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Think;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DEPLOYMENT_ID", matches = ".+")
public class DeploymentStreamingChatModelITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DEPLOYMENT_ID = System.getenv("WATSONX_DEPLOYMENT_ID");
    static final String GRANITE_3_3_DEPLOYMENT_ID = System.getenv()
            .getOrDefault("WATSONX_GRANITE_3_3_DEPLOYMENT_ID", DEPLOYMENT_ID);

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "30s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.deployment-chat-model.deployment-id", DEPLOYMENT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"think-model\".deployment-chat-model.deployment-id",
                    GRANITE_3_3_DEPLOYMENT_ID)
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.watsonx.\"think-model\".deployment-chat-model.thinking.tags.think.opening",
                    "<think>")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.watsonx.\"think-model\".deployment-chat-model.thinking.tags.think.closing",
                    "</think>")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.watsonx.\"think-model\".deployment-chat-model.thinking.tags.response.opening",
                    "<response>")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.watsonx.\"think-model\".deployment-chat-model.thinking.tags.response.closing",
                    "</response>")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    StreamingChatModel deploymentChatModel;

    @Inject
    @ModelName("think-model")
    StreamingChatModel thinkingChatModel;

    @Test
    void test_chat() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello!"))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<>();

        deploymentChatModel.chat(request, new StreamingChatResponseHandler() {
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
            }
        });

        assertDoesNotThrow(() -> latch.await(5, TimeUnit.SECONDS));
        assertTrue(!chatResponse.get().aiMessage().text().isBlank());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WATSONX_GRANITE_3_3_DEPLOYMENT_ID", matches = ".+")
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
    @EnabledIfEnvironmentVariable(named = "WATSONX_GRANITE_3_3_DEPLOYMENT_ID", matches = ".+")
    void test_chat_thinking_with_parameters() {

        WatsonxChatRequestParameters parameters = WatsonxChatRequestParameters.builder()
                .thinking(ExtractionTags.of(new Think("<think>", "</think>"), new Response("<response>", "</response>")))
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
}

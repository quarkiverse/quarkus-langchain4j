package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DEPLOYMENT_ID", matches = ".+")
public class DeploymentChatModelITTest {

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
    ChatModel deploymentChatModel;

    @Inject
    @ModelName("think-model")
    ChatModel thinkingChatModel;

    @Test
    void test_chat() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello!"))
                .build();

        var chatResponse = assertDoesNotThrow(() -> deploymentChatModel.chat(request));
        var assistantMessage = chatResponse.aiMessage();
        assertTrue(!assistantMessage.text().isBlank());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WATSONX_GRANITE_3_3_DEPLOYMENT_ID", matches = ".+")
    void test_chat_thinking() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Why the sky is blue?"))
                .build();

        var chatResponse = assertDoesNotThrow(() -> thinkingChatModel.chat(request));
        var text = chatResponse.aiMessage().text();

        assertNotNull(chatResponse);
        assertNotNull(text);
        assertFalse(text.isBlank());
        assertFalse(text.contains("<think>") && text.contains("</think>"));
        assertFalse(text.contains("<response>") && text.contains("</response>"));

        var thinkingMessage = chatResponse.aiMessage().thinking();
        assertNotNull(thinkingMessage);
        assertFalse(thinkingMessage.isBlank());
    }
}

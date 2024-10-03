package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxStreamingChatModel;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.NoopPromptFormatter;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class PromptFormatterForceDefaultTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()

            .overrideRuntimeConfigKey("quarkus.langchain4j.model1.chat-model.provider", "watsonx")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.model1.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.model1.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.model1.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.model1.project-id", WireMockUtil.PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.model1.chat-model.prompt-formatter", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.model2.chat-model.provider", "watsonx")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.model2.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.model2.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.model2.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.model2.project-id", WireMockUtil.PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.model2.chat-model.prompt-formatter", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(WireMockUtil.class));

    @RegisterAiService(modelName = "model1")
    @Singleton
    interface AIServiceWithTokenInSystemMessage {
        @SystemMessage("<|system|>This is a systemMessage")
        @UserMessage("{text}")
        String chat(String text);
    }

    @RegisterAiService(modelName = "model2")
    @Singleton
    interface AIServiceWithTokenInUserMessage {
        @SystemMessage("This is a systemMessage")
        @UserMessage("<|system|>{text}")
        String chat(String text);
    }

    @Inject
    @ModelName("model1")
    ChatLanguageModel model1ChatModel;

    @Inject
    @ModelName("model1")
    StreamingChatLanguageModel model1StreamingChatModel;

    @Inject
    @ModelName("model2")
    ChatLanguageModel model2ChatModel;

    @Inject
    @ModelName("model2")
    StreamingChatLanguageModel model2StreamingChatModel;

    @Test
    void prompt_formatter_model_1() {
        var unwrapChatModel = (WatsonxChatModel) ClientProxy.unwrap(model1ChatModel);
        assertTrue(unwrapChatModel.getPromptFormatter() instanceof NoopPromptFormatter);

        var unwrapStreamingChatModel = (WatsonxStreamingChatModel) ClientProxy.unwrap(model1StreamingChatModel);
        assertTrue(unwrapStreamingChatModel.getPromptFormatter() instanceof NoopPromptFormatter);
    }

    @Test
    void prompt_formatter_model_2() {
        var unwrapChatModel = (WatsonxChatModel) ClientProxy.unwrap(model2ChatModel);
        assertTrue(unwrapChatModel.getPromptFormatter() instanceof NoopPromptFormatter);

        var unwrapStreamingChatModel = (WatsonxStreamingChatModel) ClientProxy.unwrap(model2StreamingChatModel);
        assertTrue(unwrapStreamingChatModel.getPromptFormatter() instanceof NoopPromptFormatter);
    }
}

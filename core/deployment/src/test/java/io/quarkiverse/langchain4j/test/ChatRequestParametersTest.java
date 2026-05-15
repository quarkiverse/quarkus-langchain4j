package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusUnitTest;

public class ChatRequestParametersTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            ServiceWithParams.class,
                            ServiceWithoutParams.class,
                            ServiceWithParamsAndTemplateVars.class,
                            ServiceWithParamsAndTools.class,
                            CapturingModelSupplier.class,
                            MyTool.class));

    static volatile ChatRequest lastCapturedRequest;

    @Inject
    ServiceWithParams serviceWithParams;

    @Inject
    ServiceWithoutParams serviceWithoutParams;

    @Inject
    ServiceWithParamsAndTemplateVars serviceWithParamsAndTemplateVars;

    @Inject
    ServiceWithParamsAndTools serviceWithParamsAndTools;

    @Test
    @ActivateRequestContext
    void chatRequestParametersArePropagated() {
        lastCapturedRequest = null;

        ChatRequestParameters params = ChatRequestParameters.builder()
                .temperature(0.42)
                .topP(0.9)
                .build();

        serviceWithParams.chat("hello", params);

        assertThat(lastCapturedRequest).isNotNull();
        assertThat(lastCapturedRequest.parameters()).isNotNull();
        assertThat(lastCapturedRequest.parameters().temperature()).isEqualTo(0.42);
        assertThat(lastCapturedRequest.parameters().topP()).isEqualTo(0.9);
    }

    @Test
    @ActivateRequestContext
    void withoutChatRequestParametersStillWorks() {
        lastCapturedRequest = null;

        serviceWithoutParams.chat("hello");

        assertThat(lastCapturedRequest).isNotNull();
        assertThat(lastCapturedRequest.parameters()).isNotNull();
    }

    @Test
    @ActivateRequestContext
    void onlyUserProvidedFieldsOverrideDefaults() {
        lastCapturedRequest = null;

        ChatRequestParameters params = ChatRequestParameters.builder()
                .temperature(0.7)
                .build();

        serviceWithParams.chat("hello", params);

        assertThat(lastCapturedRequest).isNotNull();
        assertThat(lastCapturedRequest.parameters().temperature()).isEqualTo(0.7);
        assertThat(lastCapturedRequest.parameters().topP()).isNull();
    }

    @Test
    @ActivateRequestContext
    void nullParamsAreHandledGracefully() {
        lastCapturedRequest = null;

        serviceWithParams.chat("hello", null);

        assertThat(lastCapturedRequest).isNotNull();
        assertThat(lastCapturedRequest.parameters()).isNotNull();
    }

    @Test
    @ActivateRequestContext
    void templateVariablesResolvedAlongsideParams() {
        lastCapturedRequest = null;

        ChatRequestParameters params = ChatRequestParameters.builder()
                .temperature(0.5)
                .build();

        serviceWithParamsAndTemplateVars.chat("Alice", 30, params);

        assertThat(lastCapturedRequest).isNotNull();
        assertThat(lastCapturedRequest.parameters().temperature()).isEqualTo(0.5);

        List<ChatMessage> messages = lastCapturedRequest.messages();
        String userMessageText = ((UserMessage) messages.get(messages.size() - 1)).singleText();
        assertThat(userMessageText).isEqualTo("Hello Alice, you are 30 years old");
    }

    @Test
    @ActivateRequestContext
    void toolSpecificationsPreservedWhenUserParamsProvided() {
        lastCapturedRequest = null;

        ChatRequestParameters params = ChatRequestParameters.builder()
                .temperature(0.3)
                .build();

        serviceWithParamsAndTools.chat("use tools", params);

        assertThat(lastCapturedRequest).isNotNull();
        assertThat(lastCapturedRequest.parameters().temperature()).isEqualTo(0.3);
        assertThat(lastCapturedRequest.toolSpecifications()).isNotEmpty();
        assertThat(lastCapturedRequest.toolSpecifications().get(0).name()).isEqualTo("greet");
    }

    @Test
    @ActivateRequestContext
    void toolSpecificationsPreservedWithoutUserParams() {
        lastCapturedRequest = null;

        serviceWithParamsAndTools.chat("use tools", null);

        assertThat(lastCapturedRequest).isNotNull();
        assertThat(lastCapturedRequest.toolSpecifications()).isNotEmpty();
        assertThat(lastCapturedRequest.toolSpecifications().get(0).name()).isEqualTo("greet");
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface ServiceWithParams {
        String chat(@dev.langchain4j.service.UserMessage String userMessage, ChatRequestParameters params);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface ServiceWithoutParams {
        String chat(@dev.langchain4j.service.UserMessage String userMessage);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface ServiceWithParamsAndTemplateVars {
        @dev.langchain4j.service.UserMessage("Hello {name}, you are {age} years old")
        String chat(@V("name") String name, @V("age") int age, ChatRequestParameters params);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingModelSupplier.class)
    interface ServiceWithParamsAndTools {
        @ToolBox(MyTool.class)
        @dev.langchain4j.service.UserMessage("{message}")
        String chat(@V("message") String message, ChatRequestParameters params);
    }

    @Singleton
    public static class MyTool {
        @Tool
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static class CapturingModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new CapturingChatModel();
        }
    }

    public static class CapturingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            lastCapturedRequest = chatRequest;
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("response"))
                    .build();
        }
    }
}

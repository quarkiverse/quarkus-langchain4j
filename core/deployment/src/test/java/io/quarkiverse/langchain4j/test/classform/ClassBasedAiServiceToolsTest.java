package io.quarkiverse.langchain4j.test.classform;

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
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import io.quarkiverse.langchain4j.HandleToolExecutionError;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The tools of a class-based AI service: the {@code tools} attribute, a {@code @ToolBox} on a native method and a
 * static tool error handler declared on the class.
 */
public class ClassBasedAiServiceToolsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Assistant.class, ToolBoxAssistant.class, Calculator.class, FailingTool.class,
                            ToolCallingModelSupplier.class, MemorySupplier.class));

    @Singleton
    public static class Calculator {

        @Tool("Computes the length of a text")
        public int stringLength(String s) {
            return s.length();
        }
    }

    @Singleton
    public static class FailingTool {

        @Tool("Always fails")
        public String fail(String input) {
            throw new IllegalStateException("boom " + input);
        }
    }

    /**
     * Asks for a tool on the first round, the failing one when the user says so, and then replies with the result of
     * the tool.
     */
    public static class ToolCallingModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    List<ChatMessage> messages = request.messages();
                    ChatMessage last = messages.get(messages.size() - 1);
                    if (last instanceof ToolExecutionResultMessage result) {
                        return ChatResponse.builder().aiMessage(AiMessage.from("tool said " + result.text())).build();
                    }
                    String text = ((dev.langchain4j.data.message.UserMessage) last).singleText();
                    ToolExecutionRequest toolRequest = text.startsWith("fail")
                            ? ToolExecutionRequest.builder().id("1").name("fail").arguments("{\"input\":\"now\"}").build()
                            : ToolExecutionRequest.builder().id("1").name("stringLength")
                                    .arguments("{\"s\":\"" + text + "\"}").build();
                    return ChatResponse.builder().aiMessage(new AiMessage("", List.of(toolRequest)))
                            .finishReason(FinishReason.TOOL_EXECUTION).build();
                }
            };
        }
    }

    public static class MemorySupplier implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return MessageWindowChatMemory.withMaxMessages(10);
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = ToolCallingModelSupplier.class, chatMemoryProviderSupplier = MemorySupplier.class, tools = {
            Calculator.class, FailingTool.class })
    public static class Assistant {

        public native String chat(@UserMessage String message);

        @HandleToolExecutionError
        static ToolErrorHandlerResult onToolError(Throwable error, ToolErrorContext context) {
            return ToolErrorHandlerResult.text("handled " + error.getMessage());
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = ToolCallingModelSupplier.class, chatMemoryProviderSupplier = MemorySupplier.class)
    public static class ToolBoxAssistant {

        @ToolBox(Calculator.class)
        public native String chat(@UserMessage String message);
    }

    @Inject
    Assistant assistant;

    @Inject
    ToolBoxAssistant toolBoxAssistant;

    @Test
    @ActivateRequestContext
    void theToolsOfTheAttributeAreUsed() {
        assertThat(assistant.chat("hello")).isEqualTo("tool said 5");
    }

    @Test
    @ActivateRequestContext
    void aToolErrorHandlerDeclaredOnTheClassHandlesTheFailureOfATool() {
        assertThat(assistant.chat("fail please")).isEqualTo("tool said handled boom now");
    }

    @Test
    @ActivateRequestContext
    void theToolBoxOfANativeMethodIsUsed() {
        assertThat(toolBoxAssistant.chat("hey")).isEqualTo("tool said 3");
    }
}

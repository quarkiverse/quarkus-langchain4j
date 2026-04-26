package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class DynamicImmediateReturnToolTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, SimpleToolProvider.class,
                            SimpleChatModel.class, SimpleChatModelSupplier.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void immediateReturnToolReturnsDirectlyWithoutSecondLlmCall() {
        SimpleChatModel.callCount = 0; // Reset counter

        // When using a tool with IMMEDIATE return behavior:
        Result<String> result = aiService.chat("call-calculator");

        // The tool result is returned directly without going back to the LLM
        assertNull(result.content()); // No LLM-generated text
        assertThat(result.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).result()).isEqualTo("42"); // Direct tool result

        // LLM was called only once (to decide to use the tool)
        assertThat(SimpleChatModel.callCount).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void normalToolProcessesResultWithSecondLlmCall() {
        SimpleChatModel.callCount = 0;

        // When using a normal tool (without IMMEDIATE):
        Result<String> result = aiService.chat("call-processor");

        // The LLM processes the tool result and returns a final answer
        assertThat(result.content()).isEqualTo("The processor returned: 42");

        // LLM was called twice:
        // 1st call: decide to use tool
        // 2nd call: process tool result into final answer
        assertThat(SimpleChatModel.callCount).isEqualTo(2);
    }

    @RegisterAiService(chatLanguageModelSupplier = SimpleChatModelSupplier.class, toolProviderSupplier = SimpleToolProvider.class)
    public interface MyAiService {
        Result<String> chat(@UserMessage String message);
    }

    @Singleton
    public static class SimpleToolProvider implements Supplier<ToolProvider> {
        @Override
        public ToolProvider get() {
            return new ToolProvider() {
                @Override
                public ToolProviderResult provideTools(ToolProviderRequest request) {
                    // Tool 1: Calculator with IMMEDIATE return
                    ToolSpecification calculator = ToolSpecification.builder()
                            .name("calculator")
                            .description("Calculates and returns immediately")
                            .build();

                    ToolExecutor calculatorExecutor = (toolRequest, memoryId) -> {
                        return "42"; // Simple tool that returns a number
                    };

                    // Tool 2: Processor with normal return (goes back to LLM)
                    ToolSpecification processor = ToolSpecification.builder()
                            .name("processor")
                            .description("Processes data normally")
                            .build();

                    ToolExecutor processorExecutor = (toolRequest, memoryId) -> {
                        return "42"; // Same result, different flow
                    };

                    return ToolProviderResult.builder()
                            .add(calculator, calculatorExecutor, ReturnBehavior.IMMEDIATE)
                            .add(processor, processorExecutor) // Normal return behavior
                            .build();
                }
            };
        }
    }

    public static class SimpleChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new SimpleChatModel();
        }
    }

    public static class SimpleChatModel implements ChatModel {
        static int callCount = 0;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            callCount++;
            List<ChatMessage> messages = chatRequest.messages();

            // First call: User asks to use a tool
            if (messages.size() == 1) {
                dev.langchain4j.data.message.UserMessage userMsg = (dev.langchain4j.data.message.UserMessage) messages.get(0);
                String userMessage = userMsg.singleText();
                String toolName = userMessage.equals("call-calculator") ? "calculator" : "processor";

                // Pretend to be an LLM that decides to use a tool
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(
                                ToolExecutionRequest.builder()
                                        .name(toolName)
                                        .build())))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }

            // Second call: LLM processes the tool result (only for normal tools)
            if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) messages.get(2);
                String result = toolResult.text();

                // Pretend to be an LLM that formats the tool result into a nice answer
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("The processor returned: " + result))
                        .finishReason(FinishReason.STOP)
                        .build();
            }

            throw new RuntimeException("Unexpected number of messages: " + messages.size());
        }
    }
}

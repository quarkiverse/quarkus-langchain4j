package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.CreatedAware;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that tool execution works when no ChatMemoryProvider is configured,
 * using programmatic AiServices.builder() with a ToolProvider. This exercises
 * the LocalCommittableChatMemory path in determineCommittableChatMemory() and
 * mirrors the Camel AgentWithoutMemory + ToolProvider flow.
 */
public class ToolWithoutMemoryProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, Lists.class));

    @Test
    @ActivateRequestContext
    void toolExecutionWorksWithoutMemoryProvider() {
        MyAiService service = AiServices.builder(MyAiService.class)
                .chatModel(new MyChatModel())
                .toolProvider(ToolWithoutMemoryProviderTest::provideTools)
                .build();

        String result = service.chat("call-tool");
        assertThat(result).contains("tool-result-123456:");
    }

    @CreatedAware
    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface MyAiService {

        String chat(@UserMessage String message);
    }

    static ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolSpecification spec = ToolSpecification.builder()
                .name("callTool")
                .description("Calls a tool")
                .build();
        ToolExecutor executor = (req, memoryId) -> "tool-result-123456: " + req.arguments();
        return ToolProviderResult.builder().add(spec, executor).build();
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(
                                ToolExecutionRequest.builder()
                                        .id("tool-1")
                                        .name("callTool")
                                        .arguments("{}")
                                        .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + toolResult.text()))
                        .build();
            }
            return ChatResponse.builder().aiMessage(new AiMessage("Unexpected")).build();
        }
    }
}

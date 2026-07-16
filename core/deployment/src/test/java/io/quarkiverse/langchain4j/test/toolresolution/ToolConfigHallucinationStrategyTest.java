package io.quarkiverse.langchain4j.test.toolresolution;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class ToolConfigHallucinationStrategyTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ConfiguredService.class, HallucinatingModelSupplier.class,
                            HallucinationStrategy.class));

    @RegisterAiService(chatLanguageModelSupplier = HallucinatingModelSupplier.class, toolHallucinationStrategy = HallucinationStrategy.class)
    interface ConfiguredService {
        String chat(@UserMessage String message, @MemoryId Object id);
    }

    @ApplicationScoped
    public static class HallucinationStrategy
            implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {
        @Override
        public ToolExecutionResultMessage apply(ToolExecutionRequest request) {
            return ToolExecutionResultMessage.from(request, "handled hallucinated tool");
        }
    }

    public static class HallucinatingModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    List<ChatMessage> messages = request.messages();
                    ChatMessage last = messages.get(messages.size() - 1);
                    if (last.type() == TOOL_EXECUTION_RESULT) {
                        return ChatResponse.builder()
                                .aiMessage(new AiMessage(((ToolExecutionResultMessage) last).text()))
                                .build();
                    }
                    ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                            .id("missing")
                            .name("missing_tool")
                            .build();
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(toolRequest))
                            .finishReason(FinishReason.TOOL_EXECUTION)
                            .build();
                }
            };
        }
    }

    @Inject
    ConfiguredService service;

    @Test
    @ActivateRequestContext
    void configuresHallucinationStrategyUsingBeanClass() {
        assertEquals("handled hallucinated tool", service.chat("hello", 1));
    }
}

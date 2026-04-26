package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import io.quarkiverse.langchain4j.CreatedAware;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class ProgrammaticMaxSequentialToolsInvocationsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Tools.class, ModelSupplier.class));

    static ChatModel chatModel = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name("dummy")
                    .id("dummy")
                    .arguments("{}")
                    .build();
            TokenUsage usage = new TokenUsage(42, 42);
            return ChatResponse.builder().aiMessage(AiMessage.from(toolExecutionRequest))
                    .tokenUsage(usage)
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
    };

    @CreatedAware
    @RegisterAiService(tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface AssistantService {
        String chat(String message);
    }

    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return chatModel;
        }
    }

    @ApplicationScoped
    public static class Tools {
        static volatile int invocations = 0;

        @Tool
        public String dummy(String message) {
            invocations++;
            return "ok";
        }
    }

    @Inject
    AssistantService declarativeService;

    @Test
    @ActivateRequestContext
    public void testProgrammaticMaxSequentialToolsInvocations() {
        Tools.invocations = 0;

        AssistantService programmaticService = AiServices.builder(AssistantService.class)
                .chatModel(chatModel)
                .tools(new Tools())
                .maxSequentialToolsInvocations(15)
                .build();

        try {
            programmaticService.chat("blabla");
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage()).contains("15");
        }
        assertThat(Tools.invocations).isEqualTo(15);
    }
}

package io.quarkiverse.langchain4j.test.toolresolution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
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
import dev.langchain4j.service.tool.ToolCallsLimitExceededException;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class MaxToolCallsPerResponseGlobalConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Tools.class, ModelSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.ai-service.max-tool-calls-per-response", "3");

    static List<Integer> toolCallsCounts = new ArrayList<>();
    static int responseCount = 0;

    static ChatModel chatModel = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            responseCount++;
            List<ToolExecutionRequest> requests = new ArrayList<>();
            int toolCallsCount = toolCallsCounts.isEmpty() ? 5 : toolCallsCounts.remove(0);
            for (int i = 0; i < toolCallsCount; i++) {
                requests.add(ToolExecutionRequest.builder()
                        .name("dummy")
                        .id("dummy-" + responseCount + "-" + i)
                        .arguments("{}")
                        .build());
            }
            TokenUsage usage = new TokenUsage(42, 42);
            if (requests.isEmpty()) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("done"))
                        .tokenUsage(usage)
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(requests))
                    .tokenUsage(usage)
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
    };

    @RegisterAiService(tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface AiServiceWithGlobalConfig {
        String chat(String message);
    }

    @RegisterAiService(maxToolCallsPerResponse = 2, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface AiServiceWithAnnotationOverride {
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
    AiServiceWithGlobalConfig aiServiceWithGlobalConfig;

    @Inject
    AiServiceWithAnnotationOverride aiServiceWithAnnotationOverride;

    @Test
    @ActivateRequestContext
    public void testGlobalConfig() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(7);
        toolCallsCounts.add(0);

        Assertions.assertThatThrownBy(() -> aiServiceWithGlobalConfig.chat("test"))
                .isInstanceOf(ToolCallsLimitExceededException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("7");

        // Atomic reject: no tool runs once the cap is exceeded for the offending response.
        Assertions.assertThat(Tools.invocations).isEqualTo(0);
    }

    @Test
    @ActivateRequestContext
    public void testAnnotationOverridesGlobal() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(4);
        toolCallsCounts.add(0);

        Assertions.assertThatThrownBy(() -> aiServiceWithAnnotationOverride.chat("test"))
                .isInstanceOf(ToolCallsLimitExceededException.class)
                .hasMessageContaining("2")
                .hasMessageContaining("4");

        // Atomic reject: no tool runs once the cap is exceeded for the offending response.
        Assertions.assertThat(Tools.invocations).isEqualTo(0);
    }
}

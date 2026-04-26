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
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.ToolCallsLimitExceededException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verify that multiple AI services with different limits enforce limits independently.
 */
public class MaxToolCallsPerResponseMultipleServicesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Tools.class, ModelSupplier.class));

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

    @RegisterAiService(maxToolCallsPerResponse = 2, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface ServiceWithLimit2 {
        String chat(String message);
    }

    @RegisterAiService(maxToolCallsPerResponse = 5, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface ServiceWithLimit5 {
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
    ServiceWithLimit2 serviceWithLimit2;

    @Inject
    ServiceWithLimit5 serviceWithLimit5;

    @Test
    @ActivateRequestContext
    public void testIndependentLimitsPerService() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(10);
        toolCallsCounts.add(0);

        Assertions.assertThatThrownBy(() -> serviceWithLimit2.chat("test"))
                .isInstanceOf(ToolCallsLimitExceededException.class)
                .hasMessageContaining("2");
        Assertions.assertThat(Tools.invocations).isEqualTo(2);

        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(10);
        toolCallsCounts.add(0);

        Assertions.assertThatThrownBy(() -> serviceWithLimit5.chat("test"))
                .isInstanceOf(ToolCallsLimitExceededException.class)
                .hasMessageContaining("5");
        Assertions.assertThat(Tools.invocations).isEqualTo(5);
    }

    @Test
    @ActivateRequestContext
    public void testDifferentLimitsPerService() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(5);
        toolCallsCounts.add(0);

        serviceWithLimit5.chat("test");
        Assertions.assertThat(Tools.invocations).isEqualTo(5);

        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(5);
        toolCallsCounts.add(0);

        Assertions.assertThatThrownBy(() -> serviceWithLimit2.chat("test"))
                .isInstanceOf(ToolCallsLimitExceededException.class)
                .hasMessageContaining("2");
        Assertions.assertThat(Tools.invocations).isEqualTo(2);
    }
}

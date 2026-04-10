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
 * Verify functionality of {@link RegisterAiService#maxToolCallsPerResponse()}
 */
public class MaxToolCallsPerResponseTest {

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

    @RegisterAiService(maxToolCallsPerResponse = 3, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface AiServiceWithLimit3 {
        String chat(String message);
    }

    @RegisterAiService(maxToolCallsPerResponse = 5, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface AiServiceWithLimit5 {
        String chat(String message);
    }

    @RegisterAiService(maxToolCallsPerResponse = 0, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface AiServiceUnlimited {
        String chat(String message);
    }

    @RegisterAiService(tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface AiServiceNoLimit {
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
    AiServiceWithLimit3 aiServiceWithLimit3;

    @Inject
    AiServiceWithLimit5 aiServiceWithLimit5;

    @Inject
    AiServiceUnlimited aiServiceUnlimited;

    @Inject
    AiServiceNoLimit aiServiceNoLimit;

    @Test
    @ActivateRequestContext
    public void testLimitExceeded() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(5);
        toolCallsCounts.add(0);

        Assertions.assertThatThrownBy(() -> aiServiceWithLimit3.chat("test"))
                .isInstanceOf(ToolCallsLimitExceededException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("5");

        Assertions.assertThat(Tools.invocations).isEqualTo(3);
    }

    @Test
    @ActivateRequestContext
    public void testAtLimit() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(5);
        toolCallsCounts.add(0);

        aiServiceWithLimit5.chat("test");

        Assertions.assertThat(Tools.invocations).isEqualTo(5);
    }

    @Test
    @ActivateRequestContext
    public void testUnderLimit() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(3);
        toolCallsCounts.add(0);

        aiServiceWithLimit5.chat("test");

        Assertions.assertThat(Tools.invocations).isEqualTo(3);
    }

    @Test
    @ActivateRequestContext
    public void testUnlimited() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(10);
        toolCallsCounts.add(0);

        aiServiceUnlimited.chat("test");

        Assertions.assertThat(Tools.invocations).isEqualTo(10);
    }

    @Test
    @ActivateRequestContext
    public void testDefaultIsUnlimited() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(10);
        toolCallsCounts.add(0);

        aiServiceNoLimit.chat("test");

        Assertions.assertThat(Tools.invocations).isEqualTo(10);
    }

    @Test
    @ActivateRequestContext
    public void testCounterResets() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(3);
        toolCallsCounts.add(4);
        toolCallsCounts.add(0);

        aiServiceWithLimit5.chat("test");

        Assertions.assertThat(Tools.invocations).isEqualTo(7);
    }

    @Test
    @ActivateRequestContext
    public void testExceptionMessage() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(10);
        toolCallsCounts.add(0);

        ToolCallsLimitExceededException exception = Assertions.catchThrowableOfType(
                ToolCallsLimitExceededException.class,
                () -> aiServiceWithLimit3.chat("test"));

        Assertions.assertThat(exception.getLimit()).isEqualTo(3);
        Assertions.assertThat(exception.getAttempted()).isEqualTo(10);
        Assertions.assertThat(exception.getMessage())
                .isEqualTo("Exceeded maximum tool calls per response: 3 (attempted: 10)");
    }
}

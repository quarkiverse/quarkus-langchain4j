package io.quarkiverse.langchain4j.test.toolresolution;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
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
import io.quarkus.test.QuarkusUnitTest;

/**
 * The limit caps the number of responses that request tools. The response that answers must still be returned
 * once the limit is reached, as the streaming path already does.
 */
public class MaxToolCallingRoundTripsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(PingTool.class, CappedSupplier.class, AlwaysCallsToolSupplier.class));

    static final AtomicInteger roundTrips = new AtomicInteger();

    /**
     * Requests a tool for the first {@code toolRounds} responses, then answers.
     */
    static class ScriptedChatModel implements ChatModel {

        private final int toolRounds;

        ScriptedChatModel(int toolRounds) {
            this.toolRounds = toolRounds;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            TokenUsage usage = new TokenUsage(1, 1);
            int current = roundTrips.incrementAndGet();
            if (current > toolRounds) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("Enough."))
                        .tokenUsage(usage)
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                            .id("call-" + current)
                            .name("ping")
                            .arguments("{}")
                            .build()))
                    .tokenUsage(usage)
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
    }

    @RegisterAiService(maxToolCallingRoundTrips = 3, tools = PingTool.class, chatLanguageModelSupplier = CappedSupplier.class)
    public interface AssistantWithCap3 {
        String chat(String message);
    }

    @RegisterAiService(maxToolCallingRoundTrips = 3, tools = PingTool.class, chatLanguageModelSupplier = AlwaysCallsToolSupplier.class)
    public interface AlwaysCallsAssistantWithCap3 {
        String chat(String message);
    }

    public static class CappedSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            // uses all 3 allowed tool rounds, then answers
            return new ScriptedChatModel(3);
        }
    }

    public static class AlwaysCallsToolSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ScriptedChatModel(Integer.MAX_VALUE);
        }
    }

    @ApplicationScoped
    public static class PingTool {
        static volatile int invocations = 0;

        @Tool("Does nothing.")
        public String ping() {
            invocations++;
            return "pong";
        }
    }

    @Inject
    AssistantWithCap3 assistant;

    @Inject
    AlwaysCallsAssistantWithCap3 alwaysCallsAssistant;

    @BeforeEach
    void reset() {
        PingTool.invocations = 0;
        roundTrips.set(0);
    }

    @Test
    @ActivateRequestContext
    public void testAnswerIsReturnedAfterTheRoundTripLimit() {
        Assertions.assertThat(assistant.chat("go")).isEqualTo("Enough.");
        Assertions.assertThat(PingTool.invocations).isEqualTo(3);
    }

    @Test
    @ActivateRequestContext
    public void testRoundTripLimitIsEnforced() {
        Assertions.assertThatThrownBy(() -> alwaysCallsAssistant.chat("go")).hasMessageContaining("exceeded");
        Assertions.assertThat(PingTool.invocations).isEqualTo(3);
    }
}

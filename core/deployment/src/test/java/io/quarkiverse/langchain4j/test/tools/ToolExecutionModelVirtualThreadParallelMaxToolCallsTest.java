package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.ToolCallsLimitExceededException;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;

/**
 * Asserts that {@code maxToolCallsPerResponse} is enforced for all-virtual-thread streaming
 * batches that would otherwise fan out in parallel. Parallel mode rejects the entire batch
 * before any tool runs (instead of the serial behaviour of executing the first
 * {@code maxToolCallsPerResponse} tools and throwing on the next iteration).
 */
public class ToolExecutionModelVirtualThreadParallelMaxToolCallsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(VtTool.class, StreamingModelSupplier.class));

    @Inject
    LimitedAiService aiService;

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void parallelBatchOverLimitRejectsBeforeAnyToolRuns() {
        VtTool.invocations.set(0);
        StreamingModelSupplier.toolCallsCount = 5;

        assertThatThrownBy(() -> aiService.chat("test-" + UUID.randomUUID())
                .collect().asList().await().indefinitely())
                .isInstanceOf(ToolCallsLimitExceededException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("5");

        // Parallel mode rejects up-front rather than executing the first three tools.
        assertThat(VtTool.invocations.get()).isZero();
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void parallelBatchAtLimitRunsAllTools() {
        VtTool.invocations.set(0);
        StreamingModelSupplier.toolCallsCount = 3;

        aiService.chat("test-" + UUID.randomUUID())
                .collect().asList().await().indefinitely();

        assertThat(VtTool.invocations.get()).isEqualTo(3);
    }

    @RegisterAiService(maxToolCallsPerResponse = 3, tools = VtTool.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface LimitedAiService {
        Multi<String> chat(String message);
    }

    @ApplicationScoped
    public static class VtTool {

        static final AtomicInteger invocations = new AtomicInteger();

        @Tool
        @RunOnVirtualThread
        public String dummy(String message) {
            invocations.incrementAndGet();
            return "ok";
        }
    }

    public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {

        static volatile int toolCallsCount = 0;
        static int responseCount = 0;

        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                    responseCount++;
                    boolean alreadyExecuted = chatRequest.messages().stream()
                            .anyMatch(m -> m instanceof ToolExecutionResultMessage);
                    if (alreadyExecuted) {
                        handler.onCompleteResponse(ChatResponse.builder()
                                .aiMessage(AiMessage.from("done"))
                                .tokenUsage(new TokenUsage(0, 0))
                                .finishReason(FinishReason.STOP)
                                .build());
                        return;
                    }
                    List<ToolExecutionRequest> requests = new ArrayList<>();
                    for (int i = 0; i < toolCallsCount; i++) {
                        requests.add(ToolExecutionRequest.builder()
                                .name("dummy")
                                .id("dummy-" + responseCount + "-" + i)
                                .arguments("{\"message\":\"x\"}")
                                .build());
                    }
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from(requests))
                            .tokenUsage(new TokenUsage(0, 0))
                            .finishReason(FinishReason.TOOL_EXECUTION)
                            .build());
                }
            };
        }
    }
}

package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Pins {@code forceToolChoiceAutoAfterFirstIteration} parity for the streaming return paths
 * (TokenStream + Multi). Quarkus wires this flag from {@code @RegisterAiService.allowContinuousForcedToolCalling}
 * in {@code QuarkusAiServicesFactory.applyDelegationHooks}; the rewrite itself is performed by upstream's
 * loop. This test verifies the wiring + observed effect end-to-end on the streaming side, where the
 * non-streaming path's behaviour is already covered by upstream's {@code ToolChoiceAutoProtectionTest}.
 *
 * <p>
 * The model is asked to set {@link ToolChoice#REQUIRED} on the FIRST iteration via the programmatic
 * {@code chatRequestTransformer} hook, and to record the {@code toolChoice} it observes on the SECOND
 * iteration. With the flag on (default Quarkus behaviour), iteration 2 must carry {@link ToolChoice#AUTO};
 * with the flag off (when {@code allowContinuousForcedToolCalling=true}), iteration 2 stays as the
 * caller-supplied REQUIRED.
 */
public class StreamingForceToolChoiceAutoTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StickyTool.class, RecordingStreamingModel.class,
                            RecordingStreamingModelSupplier.class, ForcedTokenStreamAiService.class,
                            ForcedMultiAiService.class));

    @Test
    @ActivateRequestContext
    void tokenStream_with_forcing_rewrites_required_to_auto_on_iteration_two() throws Exception {
        StickyTool tool = new StickyTool();
        RecordingStreamingModel model = new RecordingStreamingModel();
        AtomicInteger transformerCalls = new AtomicInteger();
        AtomicInteger followUpAuto = new AtomicInteger();
        AtomicInteger followUpRequired = new AtomicInteger();

        ForcedTokenStreamAiService assistant = AiServices.builder(ForcedTokenStreamAiService.class)
                .streamingChatModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(50))
                .tools(tool)
                .maxSequentialToolsInvocations(5)
                .forceToolChoiceAutoAfterFirstIteration(true)
                .chatRequestTransformer((req, memId) -> {
                    int n = transformerCalls.getAndIncrement();
                    if (n == 0) {
                        return ChatRequest.builder()
                                .messages(req.messages())
                                .parameters(req.parameters().overrideWith(ChatRequestParameters.builder()
                                        .toolChoice(ToolChoice.REQUIRED)
                                        .build()))
                                .build();
                    }
                    ToolChoice tc = req.parameters().toolChoice();
                    if (tc == ToolChoice.AUTO) {
                        followUpAuto.incrementAndGet();
                    } else if (tc == ToolChoice.REQUIRED) {
                        followUpRequired.incrementAndGet();
                    }
                    return req;
                })
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant.chat("go")
                .onPartialResponse(s -> {
                })
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(30, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("done");
        assertThat(tool.calls.get()).isEqualTo(1);
        assertThat(followUpAuto.get())
                .as("follow-up streaming request must carry ToolChoice.AUTO when the flag is on")
                .isEqualTo(1);
        assertThat(followUpRequired.get())
                .as("follow-up must NOT still carry REQUIRED — the flag must rewrite it")
                .isEqualTo(0);
    }

    @Test
    @ActivateRequestContext
    void multi_with_forcing_rewrites_required_to_auto_on_iteration_two() {
        StickyTool tool = new StickyTool();
        RecordingStreamingModel model = new RecordingStreamingModel();
        AtomicInteger transformerCalls = new AtomicInteger();
        AtomicInteger followUpAuto = new AtomicInteger();
        AtomicInteger followUpRequired = new AtomicInteger();

        ForcedMultiAiService assistant = AiServices.builder(ForcedMultiAiService.class)
                .streamingChatModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(50))
                .tools(tool)
                .maxSequentialToolsInvocations(5)
                .forceToolChoiceAutoAfterFirstIteration(true)
                .chatRequestTransformer((req, memId) -> {
                    int n = transformerCalls.getAndIncrement();
                    if (n == 0) {
                        return ChatRequest.builder()
                                .messages(req.messages())
                                .parameters(req.parameters().overrideWith(ChatRequestParameters.builder()
                                        .toolChoice(ToolChoice.REQUIRED)
                                        .build()))
                                .build();
                    }
                    ToolChoice tc = req.parameters().toolChoice();
                    if (tc == ToolChoice.AUTO) {
                        followUpAuto.incrementAndGet();
                    } else if (tc == ToolChoice.REQUIRED) {
                        followUpRequired.incrementAndGet();
                    }
                    return req;
                })
                .build();

        List<String> chunks = assistant.chat("go").collect().asList().await().atMost(java.time.Duration.ofSeconds(30));

        assertThat(chunks).contains("done");
        assertThat(tool.calls.get()).isEqualTo(1);
        assertThat(followUpAuto.get())
                .as("Multi follow-up streaming request must carry ToolChoice.AUTO when the flag is on")
                .isEqualTo(1);
        assertThat(followUpRequired.get())
                .as("Multi follow-up must NOT still carry REQUIRED — the flag must rewrite it")
                .isEqualTo(0);
    }

    // ===================================================================================
    // Test fixtures
    // ===================================================================================

    public static class StickyTool {
        final AtomicInteger calls = new AtomicInteger();

        @Tool
        public String stick(String arg) {
            calls.incrementAndGet();
            return "ok";
        }
    }

    /**
     * Two-iteration model: iter 0 emits a tool call, iter 1 emits a final text. Lives as
     * a top-level (CDI-friendly) class so the QuarkusUnitTest deployment archive can resolve it.
     */
    public static class RecordingStreamingModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            int messageCount = request.messages().size();
            if (messageCount == 1) {
                ToolExecutionRequest req = ToolExecutionRequest.builder()
                        .id("c1")
                        .name("stick")
                        .arguments("{\"arg0\":\"x\"}")
                        .build();
                handler.onCompleteToolCall(new CompleteToolCall(0, req));
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from(req))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
            } else {
                handler.onPartialResponse("done");
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("done"))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
            }
        }
    }

    public static class RecordingStreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new RecordingStreamingModel();
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = RecordingStreamingModelSupplier.class)
    public interface ForcedTokenStreamAiService {
        TokenStream chat(String message);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = RecordingStreamingModelSupplier.class)
    public interface ForcedMultiAiService {
        Multi<String> chat(String message);
    }
}

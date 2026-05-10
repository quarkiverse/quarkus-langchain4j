package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Verifies that when a {@code Multi}-returning AI service triggers a follow-up streaming chat call
 * after tool execution, dynamic tool providers are re-evaluated for the second iteration. This was
 * the gap left by the previous Quarkus-owned streaming loop, which resolved tool providers exactly
 * once at request entry; the upstream-delegated streaming loop refreshes them per iteration via
 * {@code ToolService.refreshDynamicProvidersWithFactory}.
 */
public class MultiToolProviderRefreshTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StreamingModelSupplier.class, RecordingStreamingModel.class,
                            DynamicToolProviderSupplier.class, DynamicToolProvider.class,
                            DynamicMultiAiService.class));

    @RegisterAiService(streamingChatLanguageModelSupplier = StreamingModelSupplier.class, toolProviderSupplier = DynamicToolProviderSupplier.class)
    interface DynamicMultiAiService {
        Multi<String> chat(@UserMessage String message);
    }

    @Inject
    DynamicMultiAiService aiService;

    @BeforeEach
    void reset() {
        DynamicToolProvider.invocations.set(0);
        RecordingStreamingModel.requestCount.set(0);
    }

    @Test
    @ActivateRequestContext
    void dynamicProviderIsRefreshedBetweenMultiIterations() {
        List<String> chunks = aiService.chat("hello").collect().asList().await().indefinitely();
        assertThat(chunks).contains("done");

        // Two LLM rounds (initial + post-tool follow-up).
        assertThat(RecordingStreamingModel.requestCount.get()).isEqualTo(2);

        // Dynamic provider must be invoked on EACH iteration: once for the first request and once
        // for the follow-up. The previous Quarkus-owned loop only invoked it once at request entry
        // and reused the same tool list for the follow-up.
        assertThat(DynamicToolProvider.invocations.get())
                .as("dynamic provider must be invoked at least twice (initial + follow-up)")
                .isGreaterThanOrEqualTo(2);
    }

    public static class RecordingStreamingModel implements StreamingChatModel {

        static final AtomicInteger requestCount = new AtomicInteger();

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            int n = requestCount.incrementAndGet();
            List<ChatMessage> messages = request.messages();
            ChatMessage last = messages.get(messages.size() - 1);
            if (last.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
                ToolExecutionResultMessage trm = (ToolExecutionResultMessage) last;
                handler.onPartialResponse(trm.text());
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage(trm.text()))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
                return;
            }
            // First call: ask the LLM to invoke the (single) registered dynamic tool.
            ToolSpecification tool = request.toolSpecifications().get(0);
            List<ToolExecutionRequest> reqs = new ArrayList<>();
            reqs.add(ToolExecutionRequest.builder()
                    .id(tool.name() + "-call-" + n)
                    .name(tool.name())
                    .arguments("{}")
                    .build());
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(new AiMessage("call-tool", reqs))
                    .tokenUsage(new TokenUsage(0, 0))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build());
        }
    }

    public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new RecordingStreamingModel();
        }
    }

    @ApplicationScoped
    public static class DynamicToolProvider implements ToolProvider {

        static final AtomicInteger invocations = new AtomicInteger();

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            invocations.incrementAndGet();
            ToolSpecification spec = ToolSpecification.builder()
                    .name("dynamic_tool")
                    .description("Dynamic tool refreshed per iteration")
                    .build();
            ToolExecutor exec = (call, memId) -> "done";
            return ToolProviderResult.builder().add(spec, exec).build();
        }

        @Override
        public boolean isDynamic() {
            return true;
        }
    }

    @ApplicationScoped
    public static class DynamicToolProviderSupplier implements Supplier<ToolProvider> {
        @Inject
        DynamicToolProvider provider;

        @Override
        public ToolProvider get() {
            return provider;
        }
    }
}

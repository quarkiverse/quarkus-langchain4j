package io.quarkiverse.langchain4j.test.toolresolution;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Cross-path parity for {@link RegisterAiService#maxSequentialToolInvocations()}: the loop must
 * terminate after the configured number of LLM responses-with-tools on the streaming return paths
 * too (TokenStream + Multi), not just non-streaming. Without this cap, a misbehaving model that
 * always requests a tool would loop indefinitely.
 *
 * <p>
 * Both branches use the same model that always emits a single tool request. The cap of 5 means
 * exactly 5 tool invocations should occur before the loop aborts.
 */
public class MaxSequentialToolExecutionsStreamingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses());

    static StreamingChatModel streamingChatModel = new StreamingChatModel() {
        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name("dummy")
                    .id("dummy")
                    .arguments("{}")
                    .build();
            handler.onCompleteToolCall(new CompleteToolCall(0, toolExecutionRequest));
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolExecutionRequest))
                    .tokenUsage(new TokenUsage(42, 42))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build());
        }
    };

    public static class ModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return streamingChatModel;
        }
    }

    @ApplicationScoped
    public static class Tools {

        static volatile int sharedInvocations = 0;

        @Tool
        public String dummy(String message) {
            sharedInvocations++;
            return "ok";
        }
    }

    @RegisterAiService(maxSequentialToolInvocations = 5, tools = Tools.class, streamingChatLanguageModelSupplier = ModelSupplier.class)
    public interface TokenStreamAiService {
        TokenStream chat(String message);
    }

    @RegisterAiService(maxSequentialToolInvocations = 5, tools = Tools.class, streamingChatLanguageModelSupplier = ModelSupplier.class)
    public interface MultiAiService {
        Multi<String> chat(String message);
    }

    @Inject
    TokenStreamAiService tokenStreamAiService;

    @Inject
    MultiAiService multiAiService;

    @Test
    @ActivateRequestContext
    public void tokenStream_loop_terminates_after_max_sequential_tool_invocations() throws InterruptedException {
        Tools.sharedInvocations = 0;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        tokenStreamAiService.chat("blabla")
                .onPartialResponse(s -> {
                })
                .onCompleteResponse(r -> latch.countDown())
                .onError(t -> {
                    error.set(t);
                    latch.countDown();
                })
                .start();

        Assertions.assertThat(latch.await(30, TimeUnit.SECONDS))
                .as("TokenStream did not terminate within 30s")
                .isTrue();
        // The cap raises an exception once exceeded — onError must fire.
        Assertions.assertThat(error.get())
                .as("TokenStream must surface an error when the cap is hit")
                .isNotNull();
        Assertions.assertThat(Tools.sharedInvocations).isEqualTo(5);
    }

    @Test
    @ActivateRequestContext
    public void multi_loop_terminates_after_max_sequential_tool_invocations() {
        Tools.sharedInvocations = 0;
        try {
            List<String> chunks = multiAiService.chat("blabla").collect().asList()
                    .await().atMost(java.time.Duration.ofSeconds(30));
            // If the cap fired but didn't propagate, we must still have stopped at 5 invocations.
            Assertions.assertThat(chunks).as("Multi must not run past the cap").isNotNull();
        } catch (RuntimeException expected) {
            // Mutiny may wrap the cap exception; we don't care which form — only that the cap held.
        }
        Assertions.assertThat(Tools.sharedInvocations).isEqualTo(5);
    }
}

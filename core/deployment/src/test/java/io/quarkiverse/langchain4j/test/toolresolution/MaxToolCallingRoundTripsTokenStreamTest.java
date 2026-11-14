package io.quarkiverse.langchain4j.test.toolresolution;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;

public class MaxToolCallingRoundTripsTokenStreamTest {

    static final int GIVE_UP_AFTER = 50;

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(PingTool.class, AlwaysCallsToolStreamingSupplier.class));

    static final AtomicInteger roundTrips = new AtomicInteger();

    static StreamingChatModel streamingChatModel = new StreamingChatModel() {
        @Override
        public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            int current = roundTrips.incrementAndGet();
            TokenUsage usage = new TokenUsage(1, 1);
            if (current > GIVE_UP_AFTER) {
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Enough."))
                        .tokenUsage(usage)
                        .finishReason(FinishReason.STOP)
                        .build());
            } else {
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                .id("call-" + current)
                                .name("ping")
                                .arguments("{}")
                                .build()))
                        .tokenUsage(usage)
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
            }
        }
    };

    @RegisterAiService(maxToolCallingRoundTrips = 3, tools = PingTool.class, streamingChatLanguageModelSupplier = AlwaysCallsToolStreamingSupplier.class)
    public interface TokenStreamAssistantWithCap3 {
        TokenStream chat(String message);
    }

    @RegisterAiService(maxToolCallingRoundTrips = 3, executeToolsConcurrently = true, tools = PingTool.class, streamingChatLanguageModelSupplier = AlwaysCallsToolStreamingSupplier.class)
    public interface ConcurrentTokenStreamAssistantWithCap3 {
        TokenStream chat(String message);
    }

    public static class AlwaysCallsToolStreamingSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return streamingChatModel;
        }
    }

    @ApplicationScoped
    public static class PingTool {
        static volatile int invocations = 0;
        static volatile CountDownLatch capExceeded = new CountDownLatch(4);

        @Tool("Does nothing.")
        public String ping() {
            invocations++;
            capExceeded.countDown();
            return "pong";
        }
    }

    @Inject
    TokenStreamAssistantWithCap3 assistant;

    @Inject
    ConcurrentTokenStreamAssistantWithCap3 concurrentAssistant;

    @Test
    @ActivateRequestContext
    public void testTokenStreamRoundTripLimitEnforced() throws Exception {
        PingTool.invocations = 0;
        roundTrips.set(0);

        CompletableFuture<String> outcome = new CompletableFuture<>();
        try {
            assistant.chat("go")
                    .onCompleteResponse(response -> outcome.complete("returned normally"))
                    .onError(error -> outcome.complete(error.getMessage()))
                    .start();
        } catch (RuntimeException e) {
            outcome.complete(e.getMessage());
        }

        String result = outcome.get();
        Assertions.assertThat(result).contains("exceeded").contains("3");
        Assertions.assertThat(PingTool.invocations).isEqualTo(3);
    }

    @Test
    @ActivateRequestContext
    public void testConcurrentTokenStreamDoesNotExecuteToolsBeyondRoundTripLimit() throws Exception {
        PingTool.capExceeded = new CountDownLatch(4);
        roundTrips.set(0);

        CompletableFuture<String> outcome = new CompletableFuture<>();
        try {
            concurrentAssistant.chat("go")
                    .onCompleteResponse(new Consumer<ChatResponse>() {
                        @Override
                        public void accept(ChatResponse response) {
                            outcome.complete("returned normally");
                        }
                    })
                    .onError(new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable error) {
                            outcome.complete(error.getMessage());
                        }
                    })
                    .start();
        } catch (RuntimeException e) {
            outcome.complete(e.getMessage());
        }

        Assertions.assertThat(outcome.get(10, TimeUnit.SECONDS)).contains("exceeded").contains("3");
        Assertions.assertThat(PingTool.capExceeded.await(500, TimeUnit.MILLISECONDS))
                .as("a tool was executed beyond the round trip limit")
                .isFalse();
        Assertions.assertThat(PingTool.capExceeded.getCount()).isEqualTo(1);
    }
}

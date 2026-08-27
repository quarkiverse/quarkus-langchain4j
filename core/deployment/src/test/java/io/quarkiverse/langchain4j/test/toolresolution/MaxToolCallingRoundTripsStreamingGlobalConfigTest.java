package io.quarkiverse.langchain4j.test.toolresolution;

import java.util.concurrent.atomic.AtomicInteger;
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
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class MaxToolCallingRoundTripsStreamingGlobalConfigTest {

    static final int GIVE_UP_AFTER = 50;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(PingTool.class, AlwaysCallsToolStreamingSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.ai-service.max-tool-calling-round-trips", "2");

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

    @RegisterAiService(tools = PingTool.class, streamingChatLanguageModelSupplier = AlwaysCallsToolStreamingSupplier.class)
    public interface StreamingAssistantWithGlobalConfig {
        Multi<String> chat(String message);
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

        @Tool("Does nothing.")
        public String ping() {
            invocations++;
            return "pong";
        }
    }

    @Inject
    StreamingAssistantWithGlobalConfig assistant;

    @Test
    @ActivateRequestContext
    public void testStreamingGlobalConfigRoundTripLimit() {
        PingTool.invocations = 0;
        roundTrips.set(0);

        Assertions.assertThatThrownBy(
                () -> assistant.chat("go").collect().asList().await().indefinitely())
                .hasMessageContaining("exceeded");

        Assertions.assertThat(PingTool.invocations).isEqualTo(2);
    }
}

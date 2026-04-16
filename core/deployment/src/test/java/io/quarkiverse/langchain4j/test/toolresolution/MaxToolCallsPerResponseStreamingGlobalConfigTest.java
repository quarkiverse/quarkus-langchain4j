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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.ToolCallsLimitExceededException;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Verify global config applies to streaming AI services.
 */
public class MaxToolCallsPerResponseStreamingGlobalConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Tools.class, StreamingModelSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.ai-service.max-tool-calls-per-response", "3");

    static List<Integer> toolCallsCounts = new ArrayList<>();
    static int responseCount = 0;

    static StreamingChatModel streamingChatModel = new StreamingChatModel() {
        @Override
        public void chat(ChatRequest chatRequest, dev.langchain4j.model.chat.response.StreamingChatResponseHandler handler) {
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
                ChatResponse response = ChatResponse.builder()
                        .aiMessage(AiMessage.from("done"))
                        .tokenUsage(usage)
                        .finishReason(FinishReason.STOP)
                        .build();
                handler.onCompleteResponse(response);
            } else {
                ChatResponse response = ChatResponse.builder()
                        .aiMessage(AiMessage.from(requests))
                        .tokenUsage(usage)
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
                handler.onCompleteResponse(response);
            }
        }
    };

    @RegisterAiService(tools = Tools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface StreamingAiServiceWithGlobalConfig {
        Multi<String> chat(String message);
    }

    @RegisterAiService(maxToolCallsPerResponse = 2, tools = Tools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface StreamingAiServiceWithAnnotationOverride {
        Multi<String> chat(String message);
    }

    public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return streamingChatModel;
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
    StreamingAiServiceWithGlobalConfig streamingAiServiceWithGlobalConfig;

    @Inject
    StreamingAiServiceWithAnnotationOverride streamingAiServiceWithAnnotationOverride;

    @Test
    @ActivateRequestContext
    public void testStreamingGlobalConfig() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(7);
        toolCallsCounts.add(0);

        Assertions
                .assertThatThrownBy(
                        () -> streamingAiServiceWithGlobalConfig.chat("test").collect().asList().await().indefinitely())
                .isInstanceOf(ToolCallsLimitExceededException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("7");

        Assertions.assertThat(Tools.invocations).isEqualTo(3);
    }

    @Test
    @ActivateRequestContext
    public void testStreamingAnnotationOverridesGlobal() {
        Tools.invocations = 0;
        responseCount = 0;
        toolCallsCounts.clear();
        toolCallsCounts.add(4);
        toolCallsCounts.add(0);

        Assertions
                .assertThatThrownBy(
                        () -> streamingAiServiceWithAnnotationOverride.chat("test").collect().asList().await().indefinitely())
                .isInstanceOf(ToolCallsLimitExceededException.class)
                .hasMessageContaining("2")
                .hasMessageContaining("4");

        Assertions.assertThat(Tools.invocations).isEqualTo(2);
    }
}

package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Asserts that {@code quarkus.langchain4j.tools.dispatch=virtual-thread} forces a
 * non-{@code @RunOnVirtualThread} tool onto a virtual thread for the duration of the batch.
 * With the default {@code auto} mode, the same blocking tool would stay on a worker thread.
 */
public class ToolExecutionModelVirtualThreadGlobalDispatchTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class))
            .overrideConfigKey("quarkus.langchain4j.tools.dispatch", "virtual-thread");

    @Inject
    MyAiService aiService;

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void globalDispatchVirtualThreadForcesBlockingToolOntoVirtualThread() {
        String uuid = UUID.randomUUID().toString();
        String r = aiService.singleBlockingTool("mem-" + uuid, "hi - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, "quarkus-virtual-thread-");
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(BlockingTool.class)
        Multi<String> singleBlockingTool(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);
    }

    @Singleton
    public static class BlockingTool {
        @Tool("hi")
        public String hi(String m) {
            return m + " " + Thread.currentThread();
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new SimpleToolChatModel();
        }
    }

    private static class SimpleToolChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            boolean hasToolResults = messages.stream().anyMatch(m -> m instanceof ToolExecutionResultMessage);
            if (!hasToolResults) {
                dev.langchain4j.data.message.UserMessage userMessage = (dev.langchain4j.data.message.UserMessage) messages
                        .get(messages.size() - 1);
                String[] segments = userMessage.singleText().split(" - ", 2);
                String toolId = segments[0];
                String content = segments[1];
                ChatResponse response = ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(ToolExecutionRequest.builder()
                                .id("call-" + toolId)
                                .name(toolId)
                                .arguments("{\"m\":\"" + content + "\"}")
                                .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
                handler.onCompleteResponse(response);
            } else {
                ToolExecutionResultMessage last = null;
                for (ChatMessage message : messages) {
                    if (message instanceof ToolExecutionResultMessage trm) {
                        last = trm;
                    }
                }
                handler.onPartialResponse("response: ");
                handler.onPartialResponse(last != null ? last.text() : "");
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage(""))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
            }
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MessageWindowChatMemory.withMaxMessages(10);
        }
    }
}

package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class ToolReturningToolExecutionResultTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTools.class,
                            MyChatModel.class, Lists.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void blockingToolReturningToolExecutionResultPassesThroughResultText() {
        Result<String> r = aiService.chat("blockingTool");

        // The LLM receives resultText exactly — not a JSON-serialized ToolExecutionResult object
        assertThat(r.content()).isEqualTo("response: custom-result-text");
        assertThat(r.toolExecutions()).hasSize(1);
        assertThat(r.toolExecutions().get(0).result()).isEqualTo("custom-result-text");
        assertThat(r.toolExecutions().get(0).resultObject()).isEqualTo("custom-result-object");
    }

    @Test
    @ActivateRequestContext
    void uniToolReturningToolExecutionResultPassesThroughResultText() {
        Result<String> r = aiService.chat("uniTool");

        assertThat(r.content()).isEqualTo("response: uni-result-text");
        assertThat(r.toolExecutions()).hasSize(1);
        assertThat(r.toolExecutions().get(0).result()).isEqualTo("uni-result-text");
        assertThat(r.toolExecutions().get(0).resultObject()).isEqualTo("uni-result-object");
    }

    @RegisterAiService(tools = MyTools.class)
    public interface MyAiService {
        Result<String> chat(@UserMessage String toolName);
    }

    @ApplicationScoped
    public static class MyTools {

        @Tool
        public ToolExecutionResult blockingTool() {
            return ToolExecutionResult.builder()
                    .resultText("custom-result-text")
                    .result("custom-result-object")
                    .build();
        }

        @Tool
        public Uni<ToolExecutionResult> uniTool() {
            return Uni.createFrom().item(() -> ToolExecutionResult.builder()
                    .resultText("uni-result-text")
                    .result("uni-result-object")
                    .build());
        }
    }

    @ApplicationScoped
    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                String toolName = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(
                                ToolExecutionRequest.builder()
                                        .id("tool-" + toolName)
                                        .name(toolName)
                                        .arguments("{}")
                                        .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + toolResult.text()))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            throw new RuntimeException("Unexpected number of messages: " + messages.size());
        }
    }
}

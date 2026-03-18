package io.quarkiverse.langchain4j.tests.scopes.chat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
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
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusUnitTest;

public class ToolErrorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(AiService.class, MyChatModel.class,
                            MyChatModelSupplier.class));

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, shouldThrowExceptionOnEventError = true)
    @ApplicationScoped
    interface AiService {
        @ToolBox(MyToolBox.class)
        String chat(@UserMessage String userMessage);
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    static String TOOL_ID = "my-tool";

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                // Only the user message, extract the tool id from it
                String text = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                // Only the user message
                return ChatResponse.builder().aiMessage(new AiMessage("cannot be blank", List.of(ToolExecutionRequest.builder()
                        .id(TOOL_ID)
                        .name(TOOL_ID)
                        .arguments("{\"m\":\"" + text + "\"}")
                        .build()))).tokenUsage(new TokenUsage(0, 0)).finishReason(FinishReason.TOOL_EXECUTION).build();
            } else if (messages.size() == 3) {
                // user -> tool request -> tool response
                ToolExecutionResultMessage last = (ToolExecutionResultMessage) messages.get(messages.size() - 1);
                return ChatResponse.builder().aiMessage(AiMessage.from("response: " + last.text())).build();

            }
            return ChatResponse.builder().aiMessage(new AiMessage("Unexpected")).build();
        }
    }

    @ApplicationScoped
    public static class MyToolBox {
        @Tool(name = "my-tool")
        public String myTool(String m) {
            throw new RuntimeException("Failure in mytool");
        }
    }

    @Inject
    AiService aiService;

    @Test
    public void testToolError() {

        String result = aiService.chat("Hello");
        System.out.println("result: " + result);
    }

}

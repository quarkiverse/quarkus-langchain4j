package io.quarkiverse.langchain4j.test;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

class ToolProviderTest {
    @Inject
    MyServiceWithCustomToolProvider myServiceWithTools;

    @Inject
    MyServiceWithDefaultToolProviderConfig myServiceWithoutTools;

    @ApplicationScoped
    public static class MyCustomToolProviderSupplier implements Supplier<ToolProvider> {
        @Inject
        MyCustomToolProvider myCustomToolProvider;

        @Override
        public ToolProvider get() {
            return myCustomToolProvider;
        }
    }

    @ApplicationScoped
    public static class MyCustomToolProvider implements ToolProvider {
        @Inject
        MyServiceWithDefaultToolProviderConfig myServiceWithoutTools;

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            assertNotNull(myServiceWithoutTools);

            ToolSpecification toolSpecification = ToolSpecification.builder()
                    .name("get_booking_details")
                    .description("Returns booking details")
                    .build();
            ToolExecutor toolExecutor = (t, m) -> "0";
            return ToolProviderResult.builder()
                    .add(toolSpecification, toolExecutor)
                    .build();
        }
    }

    public static class TestAiSupplier implements Supplier<ChatLanguageModel> {
        @Override
        public ChatLanguageModel get() {
            return new TestAiModel();
        }
    }

    public static class TestAiModel implements ChatLanguageModel {
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("42"));
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
            ChatMessage lastMsg = messages.get(messages.size() - 1);
            boolean isLastMsgToolResponse = lastMsg.type().equals(TOOL_EXECUTION_RESULT);
            if (isLastMsgToolResponse) {
                ToolExecutionResultMessage msg = (ToolExecutionResultMessage) lastMsg;
                return new Response<>(new AiMessage(msg.text()));
            }
            ToolSpecification toolSpecification = toolSpecifications.get(0);
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(toolSpecification.name())
                    .id(toolSpecification.name())
                    .build();
            TokenUsage usage = new TokenUsage(42, 42);
            return new Response<>(AiMessage.from(toolExecutionRequest), usage, FinishReason.TOOL_EXECUTION);
        }
    }

    @RegisterAiService(toolProviderSupplier = MyCustomToolProviderSupplier.class, chatLanguageModelSupplier = TestAiSupplier.class)
    interface MyServiceWithCustomToolProvider {
        String chat(@UserMessage String msg, @MemoryId Object id);
    }

    @RegisterAiService(chatLanguageModelSupplier = BlockingChatLanguageModelSupplierTest.MyModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MyServiceWithDefaultToolProviderConfig {
        String chat(String msg);
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyServiceWithCustomToolProvider.class, MyCustomToolProvider.class,
                            BlockingChatLanguageModelSupplierTest.MyModelSupplier.class));

    @Test
    @ActivateRequestContext
    void testCall() {
        String answer = myServiceWithTools.chat("hello", 1);
        assertEquals("0", answer);
    }

    @Test
    @ActivateRequestContext
    void testCallNoTools() {
        String answer = myServiceWithoutTools.chat("hello");
        assertEquals("42", answer);
    }
}

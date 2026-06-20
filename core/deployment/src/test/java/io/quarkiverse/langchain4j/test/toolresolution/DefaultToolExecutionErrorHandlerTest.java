package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import io.quarkiverse.langchain4j.DefaultToolExecutionErrorHandler;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultToolExecutionErrorHandlerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Assistant.class, Tools.class, TestChatModel.class, MyErrorHandler.class));

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    void assistant() {
        int initialToolInvocationCount = Tools.INVOCATION_COUNTER.get();

        try {
            String hello = assistant.chat("hello");
            Assertions.fail("Expected DummyException");
        } catch (DummyException e) {
            // good!
        }
        int latestToolInvocationCount = Tools.INVOCATION_COUNTER.get();
        assertThat(latestToolInvocationCount).isEqualTo(initialToolInvocationCount + 1);

    }

    @DefaultToolExecutionErrorHandler
    public static class MyErrorHandler implements ToolExecutionErrorHandler {
        @Override
        public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
            if (error instanceof DummyException) {
                throw (DummyException) error;
            }
            throw new RuntimeException(error);
        }
    }

    @RegisterAiService
    interface Assistant {

        @ToolBox(Tools.class)
        String chat(String userMessage);

    }

    @ApplicationScoped
    public static final class Tools {

        public static final AtomicInteger INVOCATION_COUNTER = new AtomicInteger(0);

        @Tool
        String getWeather(String ignored) {
            INVOCATION_COUNTER.incrementAndGet();
            throw new DummyException();
        }
    }

    public static class DummyException extends RuntimeException {

    }

    @ApplicationScoped
    public static class TestChatModel implements ChatModel {

        private final ChatModel delegate;

        public TestChatModel() {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name("getWeather")
                    .arguments("{\"arg0\":\"Munich\"}")
                    .build();

            delegate = ChatModelMock.thatAlwaysResponds(
                    AiMessage.from(toolExecutionRequest),
                    AiMessage.from("I was not able to get the weather"));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return delegate.doChat(chatRequest);
        }
    }
}

package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import io.quarkiverse.langchain4j.HandleToolExecutionError;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusUnitTest;

public class ToolExecutionErrorHandlerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    Assistant assistant;

    @Inject
    Assistant2 assistant2;

    @Test
    @ActivateRequestContext
    void assistant() {
        assertThat(Assistant.EXECUTION_ERROR_HANDLER_CALLED).isFalse();
        int initialToolInvocationCount = Tools.INVOCATION_COUNTER.get();

        String hello = assistant.chat("hello");
        assertThat(hello).isNotNull();

        int latestToolInvocationCount = Tools.INVOCATION_COUNTER.get();
        assertThat(latestToolInvocationCount).isEqualTo(initialToolInvocationCount + 1);

        assertThat(Assistant.EXECUTION_ERROR_HANDLER_CALLED).isTrue();
    }

    @Test
    @ActivateRequestContext
    void assistant2() {
        assertThat(Assistant2.EXECUTION_ERROR_HANDLER_CALLED).isFalse();
        int initialToolInvocationCount = Tools.INVOCATION_COUNTER.get();

        String hello = assistant2.chat("hello");
        assertThat(hello).isNotNull();

        int latestToolInvocationCount = Tools.INVOCATION_COUNTER.get();
        assertThat(latestToolInvocationCount).isEqualTo(initialToolInvocationCount + 1);

        assertThat(Assistant2.EXECUTION_ERROR_HANDLER_CALLED).isTrue();
    }

    @RegisterAiService(chatLanguageModelSupplier = TestChatModelSupplier.class)
    interface Assistant {

        AtomicBoolean EXECUTION_ERROR_HANDLER_CALLED = new AtomicBoolean(false);

        @ToolBox(Tools.class)
        String chat(String userMessage);

        @HandleToolExecutionError
        static ToolErrorHandlerResult handle(ToolErrorContext c, Exception e) {
            assertThat(e).isInstanceOf(DummyException.class);
            EXECUTION_ERROR_HANDLER_CALLED.set(true);
            return ToolErrorHandlerResult.text(e.getMessage() + c.invocationContext().toString());
        }

    }

    @RegisterAiService(chatLanguageModelSupplier = TestChatModelSupplier.class)
    interface Assistant2 {

        AtomicBoolean EXECUTION_ERROR_HANDLER_CALLED = new AtomicBoolean(false);

        @ToolBox(Tools.class)
        String chat(String userMessage);

        @HandleToolExecutionError
        static String handle() {
            EXECUTION_ERROR_HANDLER_CALLED.set(true);
            return "boom";
        }

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
    public static class TestChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name("getWeather")
                    .arguments("{\"arg0\":\"Munich\"}")
                    .build();

            return ChatModelMock.thatAlwaysResponds(
                    AiMessage.from(toolExecutionRequest),
                    AiMessage.from("I was not able to get the weather"));
        }
    }
}

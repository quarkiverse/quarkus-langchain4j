package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verify functionality of {@code quarkus.langchain4j.ai-service.execute-tools-concurrently}
 */
public class ExecuteToolsConcurrentlyGlobalConfigTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Tools.class, ModelSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.ai-service.execute-tools-concurrently", "true");

    static volatile List<ChatMessage> lastMessages;

    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse chat(ChatRequest chatRequest) {
                    List<ChatMessage> messages = chatRequest.messages();
                    if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                        lastMessages = messages;
                        return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
                    }
                    return ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                            ToolExecutionRequest.builder().id("1").name("first").arguments("{}").build(),
                            ToolExecutionRequest.builder().id("2").name("second").arguments("{}").build())))
                            .build();
                }
            };
        }
    }

    @ApplicationScoped
    public static class Tools {

        static final CountDownLatch latch = new CountDownLatch(2);

        @Tool
        public String first() throws InterruptedException {
            return awaitOther("first");
        }

        @Tool
        public String second() throws InterruptedException {
            return awaitOther("second");
        }

        private String awaitOther(String name) throws InterruptedException {
            latch.countDown();
            return name + ":" + (latch.await(500, TimeUnit.MILLISECONDS) ? "concurrent" : "sequential");
        }
    }

    @RegisterAiService(tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface Assistant {
        String chat(String message);
    }

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    void toolsRunConcurrently() {
        assertThat(assistant.chat("hello")).isEqualTo("done");

        assertThat(lastMessages.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .map(ToolExecutionResultMessage::text))
                .containsExactly("first:concurrent", "second:concurrent");
    }
}

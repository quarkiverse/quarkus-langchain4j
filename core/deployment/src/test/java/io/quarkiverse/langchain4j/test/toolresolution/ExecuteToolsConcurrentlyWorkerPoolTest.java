package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;
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
 * Verify that concurrently executed tools do not starve when the AI service is invoked from the worker pool,
 * as is the case for blocking REST endpoints
 */
public class ExecuteToolsConcurrentlyWorkerPoolTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Tools.class, ModelSupplier.class))
            .overrideConfigKey("quarkus.thread-pool.max-threads", "2");

    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse chat(ChatRequest chatRequest) {
                    List<ChatMessage> messages = chatRequest.messages();
                    if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
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

        @Tool
        public String first() {
            return "first";
        }

        @Tool
        public String second() {
            return "second";
        }
    }

    @RegisterAiService(executeToolsConcurrently = true, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface Assistant {
        String chat(String message);
    }

    @Inject
    Assistant assistant;

    /**
     * Backed by the worker pool
     */
    @Inject
    ManagedExecutor workerPool;

    @Test
    @ActivateRequestContext
    void toolsRunWhileAllWorkerThreadsAreWaitingForThem() throws Exception {
        // both worker threads are occupied by AI service calls that wait for their tools
        Future<String> one = workerPool.submit(() -> assistant.chat("hello"));
        Future<String> two = workerPool.submit(() -> assistant.chat("hello"));

        assertThat(one.get(5, TimeUnit.SECONDS)).isEqualTo("done");
        assertThat(two.get(5, TimeUnit.SECONDS)).isEqualTo("done");
    }
}

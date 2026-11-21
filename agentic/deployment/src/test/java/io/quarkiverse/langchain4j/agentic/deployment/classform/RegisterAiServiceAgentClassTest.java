package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A class annotated with {@code @RegisterAiService} whose native method is an {@code @Agent} method is a class-based
 * agent, not a plain AI service in its class form: the agentic extension owns its bean, the {@code tools} and
 * {@code chatMemoryProviderSupplier} attributes of the annotation are honoured, and the agent is both a sub-agent of
 * workflows and an injectable service whose memory follows its scope.
 */
public class RegisterAiServiceAgentClassTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @Singleton
    public static class Calculator {

        @Tool("Computes the length of a text")
        public int stringLength(String s) {
            return s.length();
        }
    }

    /** Asks for the length tool on the first round, then replies with its result; records the request sizes. */
    public static class ToolCallingModelSupplier implements Supplier<ChatModel> {

        static final List<Integer> MESSAGE_COUNTS = Collections.synchronizedList(new ArrayList<>());

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    List<ChatMessage> messages = request.messages();
                    ChatMessage last = messages.get(messages.size() - 1);
                    if (last instanceof ToolExecutionResultMessage result) {
                        return ChatResponse.builder().aiMessage(AiMessage.from("length " + result.text())).build();
                    }
                    MESSAGE_COUNTS.add(messages.size());
                    String text = ((dev.langchain4j.data.message.UserMessage) last).singleText();
                    ToolExecutionRequest toolRequest = ToolExecutionRequest.builder().id("1").name("stringLength")
                            .arguments("{\"s\":\"" + text + "\"}").build();
                    return ChatResponse.builder().aiMessage(new AiMessage("", List.of(toolRequest)))
                            .finishReason(FinishReason.TOOL_EXECUTION).build();
                }
            };
        }
    }

    /** Memories backed by one store shared by every instance, so that an evicted memory is observable. */
    public static class MemorySupplier implements Supplier<ChatMemoryProvider> {

        static final InMemoryChatMemoryStore STORE = new InMemoryChatMemoryStore();

        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return MessageWindowChatMemory.builder().id(memoryId).maxMessages(10).chatMemoryStore(STORE).build();
                }
            };
        }
    }

    @RequestScoped
    @RegisterAiService(chatMemoryProviderSupplier = MemorySupplier.class, tools = Calculator.class)
    public static class Measurer {

        @UserMessage("{{text}}")
        @Agent(description = "Measures a text", outputKey = "length")
        public native String measure(@V("text") String text);

        @ChatModelSupplier
        ChatModel model() {
            return new ToolCallingModelSupplier().get();
        }
    }

    public interface Measuring {

        @SequenceAgent(outputKey = "length", subAgents = Measurer.class)
        String measure(@V("text") String text);
    }

    @Inject
    Measurer measurer;

    @Inject
    Measuring measuring;

    @BeforeEach
    void reset() {
        ToolCallingModelSupplier.MESSAGE_COUNTS.clear();
    }

    @Test
    void theToolsOfTheAnnotationAreUsedByTheAgentAsASubAgent() {
        inRequest(() -> assertThat(measuring.measure("hello")).isEqualTo("length 5"));
    }

    @Test
    void theInjectedAgentKeepsItsMemoryForTheRequestAndEvictsItAtItsEnd() {
        inRequest(() -> {
            assertThat(measurer.measure("one")).isEqualTo("length 3");
            measurer.measure("two");
        });
        inRequest(() -> measurer.measure("three"));
        // the second call of the first request sees the four messages of the first tool round trip, the next
        // request starts afresh
        assertThat(ToolCallingModelSupplier.MESSAGE_COUNTS).containsExactly(1, 5, 1);
    }

    private static void inRequest(Runnable runnable) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            runnable.run();
        } finally {
            requestContext.terminate();
        }
    }
}

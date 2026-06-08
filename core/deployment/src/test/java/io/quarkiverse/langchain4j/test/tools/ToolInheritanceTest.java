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
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that @Tool methods are inherited from superclasses.
 */
public class ToolInheritanceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            AiServiceWithChildTool.class,
                            AiServiceWithOverridingTool.class,
                            AiServiceWithOverloadedTool.class,
                            ParentTool.class,
                            ChildTool.class,
                            ParentWithOverridableTool.class,
                            ChildOverridingTool.class,
                            ParentWithProcess.class,
                            ChildWithOverloadedProcess.class,
                            Lists.class));

    @Inject
    AiServiceWithChildTool aiServiceWithChildTool;

    @Inject
    AiServiceWithOverridingTool aiServiceWithOverridingTool;

    @Inject
    AiServiceWithOverloadedTool aiServiceWithOverloadedTool;

    @Test
    @ActivateRequestContext
    void testToolInheritedFromParentClass() {
        String uuid = UUID.randomUUID().toString();
        var r = aiServiceWithChildTool.chat("abc", "parentMethod - " + uuid);
        assertThat(r).contains(uuid);
    }

    @Test
    @ActivateRequestContext
    void testToolDeclaredOnChildClass() {
        String uuid = UUID.randomUUID().toString();
        var r = aiServiceWithChildTool.chat("abc", "childMethod - " + uuid);
        assertThat(r).contains(uuid);
    }

    @Test
    @ActivateRequestContext
    void testOverriddenToolUsesChildVersion() {
        String uuid = UUID.randomUUID().toString();
        var r = aiServiceWithOverridingTool.chat("abc", "compute - " + uuid);
        assertThat(r).contains("child:" + uuid);
    }

    @Test
    @ActivateRequestContext
    void testOverloadedMethodWithDifferentToolNameFromParent() {
        String uuid = UUID.randomUUID().toString();
        var r = aiServiceWithOverloadedTool.chat("abc", "process_string - " + uuid);
        assertThat(r).contains(uuid);
    }

    @Test
    @ActivateRequestContext
    void testOverloadedMethodWithDifferentToolNameFromChild() {
        String uuid = UUID.randomUUID().toString();
        var r = aiServiceWithOverloadedTool.chat("abc", "process_int - " + uuid);
        assertThat(r).contains(uuid);
    }

    // --- Tool classes ---

    @Singleton
    public static class ParentTool {
        @Tool("parent tool")
        public String parentMethod(String m) {
            return m;
        }
    }

    @Singleton
    public static class ChildTool extends ParentTool {
        @Tool("child tool")
        public String childMethod(String m) {
            return m;
        }
    }

    @Singleton
    public static class ParentWithOverridableTool {
        @Tool("parent description")
        public String compute(String m) {
            return "parent:" + m;
        }
    }

    @Singleton
    public static class ChildOverridingTool extends ParentWithOverridableTool {
        @Override
        @Tool("child description")
        public String compute(String m) {
            return "child:" + m;
        }
    }

    @Singleton
    public static class ParentWithProcess {
        @Tool(name = "process_string", value = "process a string")
        public String process(String m) {
            return m;
        }
    }

    @Singleton
    public static class ChildWithOverloadedProcess extends ParentWithProcess {
        @Tool(name = "process_int", value = "process an int")
        public String process(String m, int value) {
            return m + ":" + value;
        }
    }

    // --- AI Services ---

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface AiServiceWithChildTool {
        @ToolBox(ChildTool.class)
        String chat(@MemoryId String memoryId, @dev.langchain4j.service.UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface AiServiceWithOverridingTool {
        @ToolBox(ChildOverridingTool.class)
        String chat(@MemoryId String memoryId, @dev.langchain4j.service.UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface AiServiceWithOverloadedTool {
        @ToolBox(ChildWithOverloadedProcess.class)
        String chat(@MemoryId String memoryId, @dev.langchain4j.service.UserMessage String msg);
    }

    // --- Mock ChatModel ---

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                String text = ((UserMessage) messages.get(0)).singleText();
                var segments = text.split(" - ");
                var toolName = segments[0];
                var content = segments[1];
                String arguments;
                if ("process_int".equals(toolName)) {
                    arguments = "{\"m\":\"" + content + "\", \"value\": 42}";
                } else {
                    arguments = "{\"m\":\"" + content + "\"}";
                }
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("cannot be blank",
                                List.of(ToolExecutionRequest.builder()
                                        .id("tool-" + toolName)
                                        .name(toolName)
                                        .arguments(arguments)
                                        .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage last = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + last.text()))
                        .build();
            }
            return ChatResponse.builder().aiMessage(new AiMessage("Unexpected")).build();
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> new NoopChatMemory();
        }
    }
}

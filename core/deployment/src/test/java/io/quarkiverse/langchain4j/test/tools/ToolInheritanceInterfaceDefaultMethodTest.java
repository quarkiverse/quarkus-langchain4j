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
 * Tests that @Tool methods on interface default methods are discovered from implementing classes.
 */
public class ToolInheritanceInterfaceDefaultMethodTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            ToolInterface.class,
                            ImplementsToolInterface.class,
                            ToolInterfaceWithStatic.class,
                            ImplementsToolInterfaceWithStatic.class,
                            ToolInterfaceForOverride.class,
                            OverridesInterfaceToolImpl.class,
                            ToolInterfaceForCombo.class,
                            ComboToolImpl.class,
                            AiServiceWithInterfaceTool.class,
                            AiServiceWithStaticInterfaceTool.class,
                            AiServiceWithOverriddenInterfaceTool.class,
                            AiServiceWithComboTool.class,
                            Lists.class));

    @Inject
    AiServiceWithInterfaceTool aiServiceWithInterfaceTool;

    @Inject
    AiServiceWithStaticInterfaceTool aiServiceWithStaticInterfaceTool;

    @Inject
    AiServiceWithOverriddenInterfaceTool aiServiceWithOverriddenInterfaceTool;

    @Inject
    AiServiceWithComboTool aiServiceWithComboTool;

    @Test
    @ActivateRequestContext
    void testToolFromInterfaceDefaultMethod() {
        String uuid = UUID.randomUUID().toString();
        var r = aiServiceWithInterfaceTool.chat("abc", "interfaceMethod - " + uuid);
        assertThat(r).contains(uuid);
    }

    @Test
    @ActivateRequestContext
    void testToolFromInterfaceStaticMethod() {
        String uuid = UUID.randomUUID().toString();
        var r = aiServiceWithStaticInterfaceTool.chat("abc", "staticInterfaceMethod - " + uuid);
        assertThat(r).contains(uuid);
    }

    @Test
    @ActivateRequestContext
    void testOverriddenInterfaceDefaultMethodUsesImplVersion() {
        String uuid = UUID.randomUUID().toString();
        var r = aiServiceWithOverriddenInterfaceTool.chat("abc", "overridableMethod - " + uuid);
        assertThat(r).contains("impl:" + uuid);
    }

    @Test
    @ActivateRequestContext
    void testClassWithOwnToolAndInterfaceTool() {
        String uuid = UUID.randomUUID().toString();
        var r = aiServiceWithComboTool.chat("abc", "ownMethod - " + uuid);
        assertThat(r).contains(uuid);

        uuid = UUID.randomUUID().toString();
        r = aiServiceWithComboTool.chat("abc", "interfaceDefault - " + uuid);
        assertThat(r).contains(uuid);
    }

    // --- Tool interfaces and classes ---

    public interface ToolInterface {
        @Tool("interface tool")
        default String interfaceMethod(String m) {
            return m;
        }
    }

    @Singleton
    public static class ImplementsToolInterface implements ToolInterface {
    }

    public interface ToolInterfaceWithStatic {
        @Tool("static interface tool")
        static String staticInterfaceMethod(String m) {
            return m;
        }
    }

    @Singleton
    public static class ImplementsToolInterfaceWithStatic implements ToolInterfaceWithStatic {
    }

    public interface ToolInterfaceForOverride {
        @Tool("overridable interface tool")
        default String overridableMethod(String m) {
            return "iface:" + m;
        }
    }

    @Singleton
    public static class OverridesInterfaceToolImpl implements ToolInterfaceForOverride {
        @Override
        @Tool("overridden by impl")
        public String overridableMethod(String m) {
            return "impl:" + m;
        }
    }

    public interface ToolInterfaceForCombo {
        @Tool("combo interface default tool")
        default String interfaceDefault(String m) {
            return m;
        }
    }

    @Singleton
    public static class ComboToolImpl implements ToolInterfaceForCombo {
        @Tool("own tool")
        public String ownMethod(String m) {
            return m;
        }
    }

    // --- AI Services ---

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface AiServiceWithInterfaceTool {
        @ToolBox(ImplementsToolInterface.class)
        String chat(@MemoryId String memoryId, @dev.langchain4j.service.UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface AiServiceWithStaticInterfaceTool {
        @ToolBox(ImplementsToolInterfaceWithStatic.class)
        String chat(@MemoryId String memoryId, @dev.langchain4j.service.UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface AiServiceWithOverriddenInterfaceTool {
        @ToolBox(OverridesInterfaceToolImpl.class)
        String chat(@MemoryId String memoryId, @dev.langchain4j.service.UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface AiServiceWithComboTool {
        @ToolBox(ComboToolImpl.class)
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
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("cannot be blank",
                                List.of(ToolExecutionRequest.builder()
                                        .id("tool-" + toolName)
                                        .name(toolName)
                                        .arguments("{\"m\":\"" + content + "\"}")
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

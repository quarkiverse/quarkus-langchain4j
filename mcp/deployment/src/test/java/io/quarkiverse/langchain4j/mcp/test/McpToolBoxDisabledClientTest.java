package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.mcp.test.mock.ModernMcpMockServer;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A {@code @McpToolBox} may legitimately name a client that is configured but disabled (for example per profile).
 * Such a client has no bean, so it must simply contribute no tools - without the tool provider tripping over a
 * {@code null} client and logging a "Failed to retrieve tools from MCP server" warning.
 */
public class McpToolBoxDisabledClientTest extends WiremockAware {

    private static final String CLIENT1_PATH = "/mcp/toolbox-disabled-client/client1";

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(BothClientsService.class)
                    .addPackage(McpMockServer.class.getPackage()))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.transport-type", "streamable-http")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.protocol-version", ModernMcpMockServer.PROTOCOL_VERSION)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.url", wiremockUrlForConfig(CLIENT1_PATH))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client2.protocol-version", ModernMcpMockServer.PROTOCOL_VERSION)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client2.url", "http://localhost/never-contacted/mcp");

    private static final Logger MCP_TOOL_PROVIDER_LOGGER = Logger.getLogger(McpToolProvider.class.getName());

    private final List<LogRecord> warnings = new CopyOnWriteArrayList<>();

    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @Inject
    BothClientsService service;

    @BeforeEach
    void setUp() {
        McpMockServer.modern(wiremock(), CLIENT1_PATH).reset().stubInitialization().stubTools(McpTool.ADD);
        MCP_TOOL_PROVIDER_LOGGER.addHandler(handler);
    }

    @AfterEach
    void tearDown() {
        MCP_TOOL_PROVIDER_LOGGER.removeHandler(handler);
    }

    @Test
    @ActivateRequestContext
    public void disabledClientContributesNoToolsAndNoWarning() {
        String[] toolNames = service.toolsList("test").split(",");
        assertThat(toolNames).containsExactly("add");
        assertThat(warnings)
                .extracting(LogRecord::getMessage)
                .noneMatch(message -> message.contains("Failed to retrieve tools from MCP server"));
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface BothClientsService {

        @McpToolBox({ "client1", "client2" })
        String toolsList(@UserMessage String userMessage);
    }

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
            List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
            String tools = toolSpecifications == null ? ""
                    : toolSpecifications.stream()
                            .map(ToolSpecification::name)
                            .collect(Collectors.joining(","));
            return ChatResponse.builder().aiMessage(new AiMessage(tools)).build();
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new NoopChatMemory();
                }
            };
        }
    }
}

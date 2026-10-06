package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.mcp.test.mock.ModernMcpMockServer;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusToolProviderRequest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies how tools of multiple MCP clients are combined and selected. Each client connects
 * to its own mock MCP server that provides a single tool.
 */
public class MultipleMcpClientsTest extends WiremockAware {

    private static final String CLIENT1_PATH = "/mcp/multiple-clients/client1";
    private static final String CLIENT2_PATH = "/mcp/multiple-clients/client2";
    private static final String CLIENT3_PATH = "/mcp/multiple-clients/client3";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AllToolsService.class, SelectedToolsService.class, SingleToolService.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.protocol-version=%1$s
                            quarkus.langchain4j.mcp.client1.url=%2$s
                            quarkus.langchain4j.mcp.client2.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client2.protocol-version=%1$s
                            quarkus.langchain4j.mcp.client2.url=%3$s
                            quarkus.langchain4j.mcp.client3.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client3.protocol-version=%1$s
                            quarkus.langchain4j.mcp.client3.url=%4$s
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            """.formatted(ModernMcpMockServer.PROTOCOL_VERSION,
                            wiremockUrlForConfig(CLIENT1_PATH),
                            wiremockUrlForConfig(CLIENT2_PATH),
                            wiremockUrlForConfig(CLIENT3_PATH))),
                            "application.properties"));

    @Inject
    ToolProvider toolProvider;

    @Inject
    AllToolsService allToolsService;

    @Inject
    SelectedToolsService selectedToolsService;

    @Inject
    SingleToolService singleToolService;

    @Inject
    NoToolService noToolService;

    @BeforeEach
    void setUpMcpServers() {
        McpMockServer.modern(wiremock(), CLIENT1_PATH).reset().stubInitialization().stubTools(McpTool.ADD);
        McpMockServer.modern(wiremock(), CLIENT2_PATH).reset().stubInitialization().stubTools(McpTool.SUBTRACT);
        McpMockServer.modern(wiremock(), CLIENT3_PATH).reset().stubInitialization().stubTools(McpTool.MULTIPLY);
    }

    @Test
    public void providingAllTools() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        assertThat(toolProviderResult.tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("add", "subtract", "multiply");
    }

    @Test
    public void providingSelectedTools() {
        var invocationContext = InvocationContext.builder()
                .chatMemoryId("1")
                .build();
        var request = new QuarkusToolProviderRequest(invocationContext, new dev.langchain4j.data.message.UserMessage("hi"),
                List.of("client1", "client3"));
        ToolProviderResult toolProviderResult = toolProvider.provideTools(request);

        assertThat(toolProviderResult.tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("add", "multiply");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface AllToolsService {

        @McpToolBox
        String toolsList(@UserMessage String userMessage);
    }

    @Test
    @ActivateRequestContext
    public void serviceHasAllTools() {
        String[] toolNames = allToolsService.toolsList("test").split(",");
        assertThat(toolNames).containsExactlyInAnyOrder("add", "subtract", "multiply");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface SelectedToolsService {

        @McpToolBox({ "client1", "client3" })
        String toolsList(@UserMessage String userMessage);
    }

    @Test
    @ActivateRequestContext
    public void serviceHasOnlySelectedTools() {
        String[] toolNames = selectedToolsService.toolsList("test").split(",");
        assertThat(toolNames).containsExactlyInAnyOrder("add", "multiply");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface SingleToolService {

        @McpToolBox("client2")
        String toolsList(@UserMessage String userMessage);
    }

    @Test
    @ActivateRequestContext
    public void serviceHasOneTool() {
        String[] toolNames = singleToolService.toolsList("test").split(",");
        assertThat(toolNames).containsExactly("subtract");
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface NoToolService {

        String toolsList(@UserMessage String userMessage);
    }

    @Test
    @ActivateRequestContext
    public void serviceHasNoTools() {
        assertThat(noToolService.toolsList("test")).hasSize(0);
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

package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIterable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
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
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusToolProviderRequest;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Test MCP clients over an HTTP transport.
 * This is a very rudimentary test that runs against a mock MCP server. The plan is
 * to replace it with a more proper MCP server once we have an appropriate Java SDK ready for it.
 */
public class MultipleMcpClientsTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMockHttpMcpServer.class, MockHttpMcpServer.class, Mock2HttpMcpServer.class,
                            Mock3HttpMcpServer.class, AllToolsService.class, SelectedToolsService.class,
                            SingleToolService.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mock-mcp/sse
                            quarkus.langchain4j.mcp.client2.transport-type=http
                            quarkus.langchain4j.mcp.client2.url=http://localhost:8081/mock2-mcp/sse
                            quarkus.langchain4j.mcp.client3.transport-type=http
                            quarkus.langchain4j.mcp.client3.url=http://localhost:8081/mock3-mcp/sse
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            quarkus.langchain4j.mcp.client1.tool-execution-timeout=1s
                            """),
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

    @Test
    public void providingAllTools() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        assertThat(toolProviderResult.tools()).hasSize(5);
        Set<String> toolNames = toolProviderResult.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        assertThatIterable(toolNames)
                .containsExactlyInAnyOrder("add", "subtract", "multiply", "longRunningOperation", "logging");
    }

    @Test
    public void providingSelectedTools() {
        var request = new QuarkusToolProviderRequest("1", new dev.langchain4j.data.message.UserMessage("hi"),
                List.of("client1", "client3"));
        ToolProviderResult toolProviderResult = toolProvider.provideTools(request);

        assertThat(toolProviderResult.tools()).hasSize(4);
        Set<String> toolNames = toolProviderResult.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        assertThatIterable(toolNames)
                .containsExactlyInAnyOrder("add", "multiply", "longRunningOperation", "logging");
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
        assertThat(toolNames)
                .hasSize(5)
                .containsExactlyInAnyOrder("add", "subtract", "multiply", "longRunningOperation", "logging");
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
        assertThat(toolNames)
                .hasSize(4)
                .containsExactlyInAnyOrder("add", "multiply", "longRunningOperation", "logging");
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
        assertThat(toolNames).hasSize(1);
        assertThat(toolNames[0]).isEqualTo("subtract");
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

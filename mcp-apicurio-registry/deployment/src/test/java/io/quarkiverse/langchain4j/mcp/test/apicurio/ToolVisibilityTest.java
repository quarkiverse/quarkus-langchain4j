package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that an AI service with explicit tools (ApicurioRegistryMcpTools)
 * also sees tools from the dynamically generated MCP tool provider.
 * This tests the fix for the !hasExplicitTools condition in AiServicesRecorder.
 */
public class ToolVisibilityTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.http.test-port=0\n"
                                            + "quarkus.langchain4j.mcp.apicurio-registry.url=http://localhost:8080/apis/registry/v3\n"),
                            "application.properties"));

    @RegisterAiService(tools = ApicurioRegistryMcpTools.class, chatLanguageModelSupplier = ToolReportingModelSupplier.class, chatMemoryProviderSupplier = NoopMemoryProviderSupplier.class)
    public interface TestAiService {

        @McpToolBox
        String chat(@UserMessage String message);
    }

    @Inject
    TestAiService testAiService;

    @Test
    @ActivateRequestContext
    void aiServiceSeesExplicitTools() {
        String toolNames = testAiService.chat("list tools");
        assertThat(toolNames).contains("searchMcpServers");
        assertThat(toolNames).contains("connectMcpServer");
        assertThat(toolNames).contains("disconnectMcpServer");
    }

    public static class ToolReportingModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ToolReportingModel();
        }
    }

    public static class ToolReportingModel implements ChatModel {
        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            var specs = chatRequest.toolSpecifications();
            String tools = specs == null ? ""
                    : specs.stream()
                            .map(s -> s.name())
                            .collect(Collectors.joining(","));
            return ChatResponse.builder().aiMessage(new AiMessage(tools)).build();
        }
    }

    public static class NoopMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MessageWindowChatMemory.withMaxMessages(10);
        }
    }
}

package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.function.Supplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A {@code @McpToolBox} that names an MCP client which is not configured at all must fail the build
 * with a message pointing at the method and the offending name, instead of silently providing no tools.
 */
public class McpToolBoxUnknownClientTest {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MisspelledToolBoxService.class, MyChatModelSupplier.class, MyChatModel.class))
            .overrideConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:1/mcp")
            .assertException(t -> {
                assertThat(t).hasMessageContaining("@McpToolBox")
                        .hasMessageContaining(MisspelledToolBoxService.class.getName() + "#toolsList")
                        .hasMessageContaining("'clientl'")
                        .hasMessageContaining("Configured MCP clients: [client1]");
            });

    @Test
    void buildShouldHaveFailed() {
        fail("Should not be called");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface MisspelledToolBoxService {

        // "clientl" (lowercase L) instead of "client1"
        @McpToolBox("clientl")
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
            return ChatResponse.builder().aiMessage(new AiMessage("")).build();
        }
    }
}

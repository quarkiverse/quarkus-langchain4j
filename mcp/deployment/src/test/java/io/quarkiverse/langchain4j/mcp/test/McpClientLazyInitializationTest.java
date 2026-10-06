package io.quarkiverse.langchain4j.mcp.test;

import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verify that when an AI service declares to use specific MCP clients,
 * calling the AI service will not trigger initialization of all other declared clients.
 * <p>
 * No MCP server stubs are registered, so initialization of the clients always fails;
 * the test only checks which clients attempted to initialize by sending a request.
 */
public class McpClientLazyInitializationTest extends WiremockAware {

    private static final String CLIENT1_PATH = "/mcp/lazy-initialization/client1";
    private static final String CLIENT2_PATH = "/mcp/lazy-initialization/client2";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.url=%s
                            quarkus.langchain4j.mcp.client2.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client2.url=%s
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            """.formatted(wiremockUrlForConfig(CLIENT1_PATH), wiremockUrlForConfig(CLIENT2_PATH))),
                            "application.properties"));

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    interface AiService {
        @McpToolBox("client1")
        String chat(String prompt);
    }

    @Inject
    AiService aiService;

    /**
     * AiService's method declares only client1,
     * so after calling it, client2 should not attempt to initialize.
     */
    @Test
    @ActivateRequestContext
    public void test() {
        aiService.chat("hello");
        wiremock().verifyThat(moreThanOrExactly(1), postRequestedFor(urlPathEqualTo(CLIENT1_PATH)));
        wiremock().verifyThat(0, postRequestedFor(urlPathEqualTo(CLIENT2_PATH)));
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("bla bla")).build();
        }

    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

}

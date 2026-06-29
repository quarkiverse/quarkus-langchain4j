package io.quarkiverse.langchain4j.mcp.test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import org.assertj.core.api.Assertions;
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
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verify that when an AI service declares to use specific MCP clients,
 * calling the AI service will not trigger initialization of all other declared clients.
 */
public class McpClientLazyInitializationTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mcp/for-client1
                            quarkus.langchain4j.mcp.client2.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client2.url=http://localhost:8081/mcp/for-client2
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            """),
                            "application.properties"));

    private static AtomicBoolean client1Initialized = new AtomicBoolean(false);
    private static AtomicBoolean client2Initialized = new AtomicBoolean(false);

    /**
     * Very dummy API just to keep track of which clients have attempted to initialize
     * (the initialization will always fail anyway).
     */
    @Path("/mcp")
    static class DummyApi {

        @Path("/for-client1")
        @POST
        public Response response1() {
            McpClientLazyInitializationTest.client1Initialized.set(true);
            return Response.serverError().build();
        }

        @Path("/for-client2")
        @POST
        public Response response2() {
            McpClientLazyInitializationTest.client2Initialized.set(true);
            return Response.serverError().build();
        }

    }

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
        Assertions.assertThat(client1Initialized.get()).isTrue();
        Assertions.assertThat(client2Initialized.get()).isFalse();
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

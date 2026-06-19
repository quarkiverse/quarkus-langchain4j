package io.quarkiverse.langchain4j.test;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.equalTo;

import java.util.function.Supplier;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Verifies that a programmatically built streaming AiService with a single shared ChatMemory
 * (no ChatMemoryProvider, no @MemoryId) can be invoked from within an active request scope, using
 * the shared memory instead of resolving a non-default request-scope memory id it cannot handle.
 */
public class StreamingSingleSharedChatMemoryInRequestScopeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StreamingAssistant.class, ChatResource.class, FakeStreamingChatModelSupplier.class));

    @Test
    public void streamingServiceWithSingleSharedMemoryDoesNotFailInsideRequestScope() {
        get("/streaming-shared-memory")
                .then()
                .statusCode(200)
                .body(equalTo("42"));
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = FakeStreamingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface StreamingAssistant {

        Multi<String> chat(String message);
    }

    @Path("/streaming-shared-memory")
    public static class ChatResource {

        private static final ChatMemory SHARED_MEMORY = MessageWindowChatMemory.withMaxMessages(20);

        @GET
        public Multi<String> chat() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(new FakeStreamingChatModel())
                    .chatMemory(SHARED_MEMORY)
                    .build();
            return assistant.chat("hello");
        }
    }

    public static class FakeStreamingChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new FakeStreamingChatModel();
        }
    }

    static class FakeStreamingChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("4");
            handler.onPartialResponse("2");
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("42")).build());
        }
    }
}

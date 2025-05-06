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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class StreamingAndBlockingChatLanguageModelSupplierTest {
    @Path("/test")
    static class MyResource {

        private final MyService service;

        MyResource(MyService service) {
            this.service = service;
        }

        @GET
        @Path("/blocking")
        public String blocking() {
            return service.blocking("what is the Answer to the Ultimate Question of Life, the Universe, and Everything?");
        }

        @GET
        @Path("/streaming")
        public Multi<String> streaming() {
            return service.streaming("what is the Answer to the Ultimate Question of Life, the Universe, and Everything?");
        }
    }

    public static class MyModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(new AiMessage("42")).build();
                }
            };
        }
    }

    public static class MyStreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                    handler.onPartialResponse("4");
                    handler.onPartialResponse("2");
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MyModelSupplier.class, streamingChatLanguageModelSupplier = MyStreamingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MyService {
        Multi<String> streaming(String msg);

        String blocking(String msg);
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyResource.class, MyService.class, MyModelSupplier.class, MyStreamingModelSupplier.class));

    @Test
    public void testCalls() {
        get("test/blocking")
                .then()
                .statusCode(200)
                .body(equalTo("42"));
        get("test/streaming")
                .then()
                .statusCode(200)
                .body(equalTo("42"));
    }
}

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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
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

    public static class MyModelSupplier implements Supplier<ChatLanguageModel> {
        @Override
        public ChatLanguageModel get() {
            return (messages) -> new Response<>(new AiMessage("42"));
        }
    }

    public static class MyStreamingModelSupplier implements Supplier<StreamingChatLanguageModel> {
        @Override
        public StreamingChatLanguageModel get() {
            return (messages, handler) -> {
                handler.onNext("4");
                handler.onNext("2");
                handler.onComplete(new Response<>(new AiMessage("")));
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

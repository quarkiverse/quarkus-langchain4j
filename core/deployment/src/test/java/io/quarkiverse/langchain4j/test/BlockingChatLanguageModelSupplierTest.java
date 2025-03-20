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
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class BlockingChatLanguageModelSupplierTest {
    @Path("/test")
    static class MyResource {

        private final MyService service;

        MyResource(MyService service) {
            this.service = service;
        }

        @GET
        public String blocking() {
            return service.chat("what is the Answer to the Ultimate Question of Life, the Universe, and Everything?");
        }
    }

    public static class MyModelSupplier implements Supplier<ChatLanguageModel> {

        @Override
        public ChatLanguageModel get() {
            return new ChatLanguageModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    return ChatResponse.builder().aiMessage(new AiMessage("42")).build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MyModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MyService {
        String chat(String msg);
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyResource.class, MyService.class, MyModelSupplier.class));

    @Test
    public void testCall() {
        get("test")
                .then()
                .statusCode(200)
                .body(equalTo("42"));
    }
}

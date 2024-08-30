package io.quarkiverse.langchain4j.test;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.containsString;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

public class StreamingChatLanguageModelToolTest {
    private static final String TOOL_NAME = "tool";

    @Path("/test")
    static class MyResource {

        private final MyService service;

        MyResource(MyService service) {
            this.service = service;
        }

        @GET
        public Multi<String> streaming() {
            return service.chat("what is the Answer to the Ultimate Question of Life, the Universe, and Everything?");
        }
    }

    public static class MyModelSupplier implements Supplier<StreamingChatLanguageModel> {
        @Override
        public StreamingChatLanguageModel get() {
            ToolExecutionRequest.Builder builder = ToolExecutionRequest.builder();
            builder.name(TOOL_NAME);
            ToolExecutionRequest toolExecutionRequest = builder.build();
            return new StreamingChatLanguageModel() {
                @Override
                public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
                        StreamingResponseHandler<AiMessage> handler) {
                    ChatMessage last = messages.get(messages.size() - 1);
                    if (last instanceof UserMessage) {
                        Log.info("requesting tool execution");
                        handler.onComplete(Response.from(AiMessage.from(toolExecutionRequest)));
                    } else if (last instanceof ToolExecutionResultMessage) {
                        Log.info("processing tool execution result");
                        String text = ((ToolExecutionResultMessage) last).text();
                        handler.onNext(text);
                        handler.onComplete(Response.from(AiMessage.from("")));
                    } else {
                        throw new IllegalStateException();
                    }
                }

                @Override
                public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, tools = MyTool.class)
    interface MyService {
        Multi<String> chat(String msg);
    }

    @Singleton
    static class MyTool {

        @Tool(name = TOOL_NAME, value = "the answer to life, the universe and everything")
        public String answer() {
            Log.info("executing tool call");
            return Uni.createFrom().item("42")
                    // delay to simulate a blocking operation, which should not lead to
                    // The current thread cannot be blocked: vert.x-eventloop-thread-0
                    .onItem().delayIt().by(Duration.ofMillis(500))
                    .await().indefinitely();
        }
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
                .body(containsString("42"));
    }

}

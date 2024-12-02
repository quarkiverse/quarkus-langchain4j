package io.quarkiverse.langchain4j.azure.openai.test;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.equalTo;

public class IsAuthProviderAvailableTest {
    @Path("/test")
    static class MyResource {

        private final MyService service;

        @Inject
        @ModelName("gpt-4o")
        StreamingChatLanguageModel streamingChatLanguageModel;

        MyResource(MyService service) {
            this.service = service;
        }

        @GET
        public Multi<String> blocking() {
            return service.chat("what is the Answer to the Ultimate Question of Life, the Universe, and Everything?");
        }
    }

    public static class MyModelSupplier implements Supplier<StreamingChatLanguageModel> {
        @Override
        public StreamingChatLanguageModel get() {
            StreamingChatLanguageModel model = CDI.current()
                    .select(StreamingChatLanguageModel.class, new ModelName.Literal("gpt-4o")).get();
            Objects.requireNonNull(model);
            return new BlockingToolStreamingChatLanguageModelAdapter(model);
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MyService {
        Multi<String> chat(String msg);
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j."gpt-4o".chat-model.provider=azure-openai
                            quarkus.langchain4j.azure-openai."gpt-4o".api-key=dummy
                            quarkus.langchain4j.azure-openai."gpt-4o".endpoint=http://localhost:1234/
                            """),
                            "application.properties")
                    .addClasses(MyResource.class, MyService.class, MyModelSupplier.class,
                            BlockingToolStreamingChatLanguageModelAdapter.class));

    @Test
    public void testCall() {
        RestAssured.get("test")
                .then()
                .statusCode(200)
                .body(equalTo("42"));
    }

    /*
     * This class is a workaround to avoid blocking the event loop thread when executing tools.
     */
    public static class BlockingToolStreamingChatLanguageModelAdapter implements StreamingChatLanguageModel {

        final StreamingChatLanguageModel delegate;

        public BlockingToolStreamingChatLanguageModelAdapter(StreamingChatLanguageModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
            delegate.generate(userMessage, wrapHandler(handler));
        }

        @Override
        public void generate(UserMessage userMessage, StreamingResponseHandler<AiMessage> handler) {
            delegate.generate(userMessage, wrapHandler(handler));
        }

        @Override
        public void generate(List<ChatMessage> list, StreamingResponseHandler<AiMessage> handler) {
            delegate.generate(list, wrapHandler(handler));
        }

        @Override
        public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
                StreamingResponseHandler<AiMessage> handler) {
            delegate.generate(messages, toolSpecifications, wrapHandler(handler));
        }

        @Override
        public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification,
                StreamingResponseHandler<AiMessage> handler) {
            delegate.generate(messages, toolSpecification, wrapHandler(handler));
        }

        private StreamingResponseHandler<AiMessage> wrapHandler(StreamingResponseHandler<AiMessage> handler) {
            return new StreamingResponseHandler<>() {
                @Override
                public void onNext(String s) {
                    handler.onNext(s);
                }

                @Override
                public void onError(Throwable throwable) {
                    handler.onError(throwable);
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    // Tool execution must not be executed on the event loop thread.
                    // To avoid blocking the event loop thread, we run the completion handler on a separate thread.
                    // this is a workaround, which might get obsolete in future versions of langchain4j
                    CompletableFuture.runAsync(() -> {
                        handler.onComplete(response);
                    }, Infrastructure.getDefaultExecutor());
                }
            };
        }
    }
}

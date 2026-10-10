package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class AsyncReturnTypeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyService.class, Person.class, MyModelSupplier.class));

    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicReference<String> modelThread = new AtomicReference<>();

    @Inject
    MyService service;

    @Inject
    Vertx vertx;

    @BeforeEach
    void reset() {
        calls.set(0);
        modelThread.set(null);
    }

    @Test
    @ActivateRequestContext
    void uniIsLazyAndReturnsTheResponse() {
        Uni<String> answer = service.uni("what is the answer?");
        assertThat(calls).hasValue(0);

        assertThat(answer.await().atMost(Duration.ofSeconds(10))).isEqualTo("42");
        assertThat(calls).hasValue(1);
    }

    @Test
    @ActivateRequestContext
    void completionStageReturnsTheResponse() throws Exception {
        CompletionStage<String> answer = service.completionStage("what is the answer?");

        assertThat(answer.toCompletableFuture().get(10, TimeUnit.SECONDS)).isEqualTo("42");
    }

    @Test
    @ActivateRequestContext
    void uniParsesStructuredOutput() {
        Person person = service.person("extract the person").await().atMost(Duration.ofSeconds(10));

        assertThat(person.name()).isEqualTo("John");
        assertThat(person.age()).isEqualTo(42);
    }

    @Test
    void uniDoesNotCallTheModelOnTheEventLoop() throws Exception {
        CompletableFuture<String> answer = new CompletableFuture<>();
        vertx.runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void ignored) {
                Arc.container().requestContext().activate();
                try {
                    service.uni("what is the answer?").subscribe().with(new Consumer<String>() {
                        @Override
                        public void accept(String item) {
                            answer.complete(item);
                        }
                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable failure) {
                            answer.completeExceptionally(failure);
                        }
                    });
                } finally {
                    Arc.container().requestContext().terminate();
                }
            }
        });

        assertThat(answer.get(10, TimeUnit.SECONDS)).isEqualTo("42");
        assertThat(modelThread.get()).doesNotContain("eventloop");
    }

    @Test
    @ActivateRequestContext
    void uniFailsWhenTheModelFails() {
        assertThatThrownBy(() -> service.uni("fail").await().atMost(Duration.ofSeconds(10)))
                .hasMessageContaining("model failure");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MyService {

        Uni<String> uni(String msg);

        CompletionStage<String> completionStage(String msg);

        Uni<Person> person(String msg);
    }

    public record Person(String name, int age) {
    }

    public static class MyModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    calls.incrementAndGet();
                    modelThread.set(Thread.currentThread().getName());
                    String text = ((UserMessage) chatRequest.messages().get(chatRequest.messages().size() - 1))
                            .singleText();
                    if (text.contains("fail")) {
                        throw new IllegalStateException("model failure");
                    }
                    String answer = text.contains("person") ? "{\"name\":\"John\",\"age\":42}" : "42";
                    return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
                }
            };
        }
    }
}

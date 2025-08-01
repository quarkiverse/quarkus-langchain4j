package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class OutputGuardrailAccumulatorTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyStreamingChatModelSupplier.class, MyMemoryProviderSupplier.class, MyGuardrail.class,
                            SizeBasedAccumulator.class, SeparatorAccumulator.class));

    @Inject
    MyAiService ai;
    @Inject
    MyGuardrail guardrail;

    @Test
    @ActivateRequestContext
    void testWithoutAccumulator() {
        guardrail.reset();
        var list = ai.usingDefaultAccumulator("Roger")
                .collect().asList()
                .await().indefinitely();
        assertThat(guardrail.count()).isEqualTo(1);
        assertThat(guardrail.chunks()).containsExactly("Streaming world!");
        assertThat(list).containsExactly("Streaming world!");
    }

    @Test
    @ActivateRequestContext
    void testWithSizeBasedAccumulator() {
        guardrail.reset();
        var list = ai.usingSizeAccumulator("Roger")
                .collect().asList()
                .await().indefinitely();
        assertThat(guardrail.count()).isEqualTo(4);
        assertThat(guardrail.chunks()).containsExactly("Strea", "ming ", "world", "!");
        assertThat(list).containsExactly("Strea", "ming ", "world", "!");
    }

    @Test
    @ActivateRequestContext
    void testWithSeparatorBasedAccumulator() {
        guardrail.reset();
        var list = ai.usingSeparatorAccumulator("Roger")
                .collect().asList()
                .await().indefinitely();
        assertThat(guardrail.count()).isEqualTo(2);
        assertThat(guardrail.chunks()).containsExactly("Streaming", "world!");
        assertThat(list).containsExactly("Streaming", "world!");
    }

    @Test
    @ActivateRequestContext
    void testWithFailingAccumulator() {
        guardrail.reset();
        assertThatThrownBy(() -> {
            ai.usingFailingAccumulator("Roger")
                    .collect().asList()
                    .await().indefinitely();
        }).isInstanceOf(IllegalArgumentException.class);
        assertThat(guardrail.count()).isEqualTo(3);
        assertThat(guardrail.chunks()).containsExactly("Stream", "ing", " ");
    }

    @Test
    @ActivateRequestContext
    void testWithThrowingAccumulator() {
        guardrail.reset();
        assertThatThrownBy(() -> {
            ai.usingThrowingAccumulator("Roger")
                    .collect().asList()
                    .await().indefinitely();
        }).isInstanceOf(IllegalArgumentException.class);
        assertThat(guardrail.count()).isEqualTo(0);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyStreamingChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrails(MyGuardrail.class)
        Multi<String> usingDefaultAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(MyGuardrail.class)
        @OutputGuardrailAccumulator(SizeBasedAccumulator.class)
        Multi<String> usingSizeAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(MyGuardrail.class)
        @OutputGuardrailAccumulator(SeparatorAccumulator.class)
        Multi<String> usingSeparatorAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(MyGuardrail.class)
        @OutputGuardrailAccumulator(FailingAccumulator.class)
        Multi<String> usingFailingAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(MyGuardrail.class)
        @OutputGuardrailAccumulator(ThrowingAccumulator.class)
        Multi<String> usingThrowingAccumulator(@MemoryId String mem);
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new MessageWindowChatMemory.Builder().maxMessages(5).build();
                }
            };
        }
    }

    public static class MyStreamingChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                    handler.onPartialResponse("Stream");
                    handler.onPartialResponse("ing");
                    handler.onPartialResponse(" ");
                    handler.onPartialResponse("world");
                    handler.onPartialResponse("!");
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
                }
            };
        }
    }

    @ApplicationScoped
    public static class MyGuardrail implements OutputGuardrail {

        AtomicInteger count = new AtomicInteger();
        List<String> chunks = new ArrayList<>();

        public int count() {
            return count.get();
        }

        public List<String> chunks() {
            return chunks;
        }

        public void reset() {
            count.set(0);
            chunks.clear();
        }

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest request) {
            count.incrementAndGet();
            chunks.add(request.responseFromLLM().aiMessage().text());
            return success();
        }
    }

    @ApplicationScoped
    public static class SizeBasedAccumulator implements OutputTokenAccumulator {

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens
                    .withContext((multi, context) -> {
                        context.put("buffer", new StringBuffer());
                        return multi
                                .flatMap(token -> {
                                    StringBuffer buffer = context.get("buffer");
                                    buffer.append(token);
                                    if (buffer.length() >= 5) {
                                        var item = buffer.substring(0, 5);
                                        buffer.delete(0, 5);
                                        return Multi.createFrom().item(item);
                                    }
                                    return Multi.createFrom().empty();
                                })
                                .onCompletion().switchTo(() -> {
                                    StringBuffer buffer = context.get("buffer");
                                    if (!buffer.isEmpty()) {
                                        return Multi.createFrom().item(buffer.toString());
                                    }
                                    return Multi.createFrom().empty();
                                });
                    });

        }
    }

    @ApplicationScoped
    public static class SeparatorAccumulator implements OutputTokenAccumulator {

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens
                    .withContext((multi, context) -> {
                        context.put("buffer", new StringBuffer());
                        return multi
                                .flatMap(token -> {
                                    StringBuffer buffer = context.get("buffer");
                                    buffer.append(token);
                                    var idx = buffer.indexOf(" ");
                                    if (idx != -1) {
                                        var item = buffer.substring(0, idx + 1).trim(); // Drop the space
                                        buffer.delete(0, idx + 1);
                                        return Multi.createFrom().item(item);
                                    }
                                    return Multi.createFrom().empty();
                                })
                                .onCompletion().switchTo(() -> {
                                    StringBuffer buffer = context.get("buffer");
                                    if (!buffer.isEmpty()) {
                                        return Multi.createFrom().item(buffer.toString().trim());
                                    }
                                    return Multi.createFrom().empty();
                                });
                    });

        }
    }

    @ApplicationScoped
    public static class FailingAccumulator implements OutputTokenAccumulator {

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens
                    .map(s -> {
                        if (s.contains("w")) {
                            throw new IllegalArgumentException("I don't like W");
                        }
                        return s;
                    });
        }
    }

    @ApplicationScoped
    public static class ThrowingAccumulator implements OutputTokenAccumulator {

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            throw new IllegalArgumentException("Boom");
        }
    }
}

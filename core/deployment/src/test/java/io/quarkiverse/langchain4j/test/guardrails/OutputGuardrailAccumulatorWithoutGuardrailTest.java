package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class OutputGuardrailAccumulatorWithoutGuardrailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            MyMemoryProviderSupplier.class))
            .assertException(t -> {
                assertThat(t).isInstanceOf(DeploymentException.class);
                assertThat(t).hasMessageContaining(
                        "io.quarkiverse.langchain4j.test.guardrails.OutputGuardrailAccumulatorWithoutGuardrailTest$MyAiService.hi");
            });

    @Test
    @ActivateRequestContext
    void testThatInvalidAccumulatorAreReported() {
        fail("Should not be called");
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyStreamingChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrailAccumulator(MyAccumulator.class)
        Multi<String> hi(@MemoryId String mem);

    }

    @ApplicationScoped
    public static class MyAccumulator implements OutputTokenAccumulator {

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens;
        }
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
                public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
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

}

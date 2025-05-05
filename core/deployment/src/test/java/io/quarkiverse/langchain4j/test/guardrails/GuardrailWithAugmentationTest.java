package io.quarkiverse.langchain4j.test.guardrails;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Verify that the input and output guardrails can access the augmentation results.
 */
public class GuardrailWithAugmentationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class));

    @Inject
    MyInputGuardrail inputGuardrail;
    @Inject
    MyOutputGuardrail outputGuardrail;

    @Inject
    MyAiService service;

    @Test
    @ActivateRequestContext
    void testInputOnly() {
        String s = service.inputOnly("1", "foo");

        assertThat(s).isEqualTo("Hi!");
        assertThat(inputGuardrail.getSpy()).isEqualTo(1);
        assertThat(outputGuardrail.getSpy()).isEqualTo(0);
    }

    @Test
    @ActivateRequestContext
    void testInputOnlyMulti() {
        List<String> list = service.inputOnlyMulti("2", "foo").collect().asList().await().indefinitely();

        assertThat(inputGuardrail.getSpy()).isEqualTo(1);
        assertThat(outputGuardrail.getSpy()).isEqualTo(0);
        assertThat(String.join(" ", list)).isEqualTo("Streaming hi !");
    }

    @Test
    @ActivateRequestContext
    void testOutputOnly() {
        String s = service.outputOnly("3", "foo");

        assertThat(s).isEqualTo("Hi!");
        assertThat(inputGuardrail.getSpy()).isEqualTo(0);
        assertThat(outputGuardrail.getSpy()).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testInputAndOutput() {
        String s = service.inputAndOutput("4", "foo");

        assertThat(s).isEqualTo("Hi!");
        assertThat(inputGuardrail.getSpy()).isEqualTo(1);
        assertThat(outputGuardrail.getSpy()).isEqualTo(1);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, streamingChatLanguageModelSupplier = MyStreamingChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class, retrievalAugmentor = MyRetrievalAugmentor.class)
    public interface MyAiService {

        @InputGuardrails(MyInputGuardrail.class)
        String inputOnly(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyInputGuardrail.class)
        Multi<String> inputOnlyMulti(@MemoryId String id, @UserMessage String message);

        @OutputGuardrails(MyOutputGuardrail.class)
        String outputOnly(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyInputGuardrail.class)
        @OutputGuardrails(MyOutputGuardrail.class)
        String inputAndOutput(@MemoryId String id, @UserMessage String message);
    }

    @RequestScoped
    public static class MyInputGuardrail implements InputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public InputGuardrailResult validate(InputGuardrailParams params) {
            spy.incrementAndGet();
            assertThat(params.augmentationResult().contents()).hasSize(2);
            return InputGuardrailResult.success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyOutputGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            spy.incrementAndGet();
            assertThat(params.augmentationResult().contents()).hasSize(2);
            return OutputGuardrailResult.success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyStreamingChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new MyStreamingChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            assertThat(chatMessageToText(chatRequest.messages().get(chatRequest.messages().size() - 1))).isEqualTo("augmented");
            return ChatResponse.builder().aiMessage(new AiMessage("Hi!")).build();
        }
    }

    public static class MyStreamingChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            assertThat(chatMessageToText(chatRequest.messages().get(chatRequest.messages().size() - 1))).isEqualTo("augmented");
            handler.onPartialResponse("Streaming hi");
            handler.onPartialResponse("!");
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
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

    public static class MyRetrievalAugmentor implements Supplier<RetrievalAugmentor> {
        @Override
        public RetrievalAugmentor get() {
            return new RetrievalAugmentor() {
                @Override
                public AugmentationResult augment(AugmentationRequest augmentationRequest) {
                    List<Content> content = List.of(Content.from("content1"), Content.from("content2"));
                    return new AugmentationResult(dev.langchain4j.data.message.UserMessage.userMessage("augmented"), content);
                }
            };
        }
    }
}

package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Multimodal parameters must reach the model even when the method carries no {@code @UserMessage}.
 */
public class UnannotatedContentParameterTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            UnannotatedContentService.class,
                            UnannotatedImageService.class,
                            CapturingModelSupplier.class));

    private static final String IMAGE_URL = "http://example.com/cat.png";

    static volatile ChatRequest lastCapturedRequest;

    @Inject
    UnannotatedContentService unannotatedContentService;

    @Inject
    UnannotatedImageService unannotatedImageService;

    @Test
    @ActivateRequestContext
    void contentParameterWithoutUserMessageIsNotStringified() {
        lastCapturedRequest = null;

        unannotatedContentService.describe(ImageContent.from(IMAGE_URL));

        assertThat(contentsOfLastUserMessage()).containsExactly(ImageContent.from(IMAGE_URL));
    }

    @Test
    @ActivateRequestContext
    void imageParameterWithoutUserMessageStillReachesTheModel() {
        lastCapturedRequest = null;

        unannotatedImageService.describe(Image.builder().url(IMAGE_URL).build());

        assertThat(contentsOfLastUserMessage())
                .containsExactly(ImageContent.from(Image.builder().url(IMAGE_URL).build()));
    }

    private List<Content> contentsOfLastUserMessage() {
        assertThat(lastCapturedRequest).isNotNull();
        List<ChatMessage> messages = lastCapturedRequest.messages();
        return ((UserMessage) messages.get(messages.size() - 1)).contents();
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface UnannotatedContentService {
        String describe(ImageContent image);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface UnannotatedImageService {
        String describe(Image image);
    }

    public static class CapturingModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new CapturingChatModel();
        }
    }

    public static class CapturingChatModel implements ChatModel {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            lastCapturedRequest = chatRequest;
            return ChatResponse.builder().aiMessage(AiMessage.from("a cat")).build();
        }
    }
}

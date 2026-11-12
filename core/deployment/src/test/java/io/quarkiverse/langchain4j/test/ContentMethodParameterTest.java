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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * {@code Content}-typed method parameters must end up in the {@link UserMessage}, the same way
 * {@code Image}, {@code Audio}, {@code Video} and {@code PdfFile} parameters do, and the
 * same way plain LangChain4j AI Services behave.
 */
public class ContentMethodParameterTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            SingleContentService.class,
                            ListOfContentsService.class,
                            AnnotatedContentService.class,
                            CapturingModelSupplier.class));

    private static final String IMAGE_URL = "http://example.com/cat.png";

    static volatile ChatRequest lastCapturedRequest;

    @Inject
    SingleContentService singleContentService;

    @Inject
    ListOfContentsService listOfContentsService;

    @Inject
    AnnotatedContentService annotatedContentService;

    @Test
    @ActivateRequestContext
    void singleContentParameterIsAddedToUserMessage() {
        lastCapturedRequest = null;

        singleContentService.describe(ImageContent.from(IMAGE_URL));

        assertThat(contentsOfLastUserMessage())
                .containsExactly(TextContent.from("Describe this"), ImageContent.from(IMAGE_URL));
    }

    @Test
    @ActivateRequestContext
    void listOfContentsParameterIsAddedToUserMessage() {
        lastCapturedRequest = null;

        listOfContentsService.describe(List.of(ImageContent.from(IMAGE_URL), TextContent.from("extra")));

        assertThat(contentsOfLastUserMessage())
                .containsExactly(
                        TextContent.from("Describe these"),
                        ImageContent.from(IMAGE_URL),
                        TextContent.from("extra"));
    }

    @Test
    @ActivateRequestContext
    void explicitlyAnnotatedContentParameterStillWorks() {
        lastCapturedRequest = null;

        annotatedContentService.describe(ImageContent.from(IMAGE_URL));

        assertThat(contentsOfLastUserMessage())
                .containsExactly(TextContent.from("Describe this"), ImageContent.from(IMAGE_URL));
    }

    private List<Content> contentsOfLastUserMessage() {
        assertThat(lastCapturedRequest).isNotNull();
        List<ChatMessage> messages = lastCapturedRequest.messages();
        return ((UserMessage) messages.get(messages.size() - 1)).contents();
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface SingleContentService {
        @dev.langchain4j.service.UserMessage("Describe this")
        String describe(ImageContent image);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface ListOfContentsService {
        @dev.langchain4j.service.UserMessage("Describe these")
        String describe(List<Content> contents);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface AnnotatedContentService {
        @dev.langchain4j.service.UserMessage("Describe this")
        String describe(@dev.langchain4j.service.UserMessage ImageContent image);
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

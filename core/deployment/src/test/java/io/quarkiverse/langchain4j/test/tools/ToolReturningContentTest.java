package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A {@code @Tool} method whose result already is, or is made of, {@link Content} elements must be sent to
 * the LLM as those contents, exactly as upstream {@code DefaultToolExecutor#toContents} does. Otherwise the
 * result is JSON-serialized into the tool result text and the model never actually receives the image.
 * <p>
 * The stub model echoes back a description of the {@link ToolExecutionResultMessage} contents it was handed,
 * so each test asserts on what the LLM really received. {@code ToolExecutionResultMessage#text()} is avoided
 * on purpose: it throws for anything other than a single {@link TextContent}.
 */
public class ToolReturningContentTest {

    private static final String IMAGE_URL = "https://example.com/cat.png";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTools.class, Animal.class,
                            MyChatModelSupplier.class, MyChatModel.class, Lists.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void imageReturningToolIsSentAsImageContent() {
        assertThat(aiService.chat("imageTool")).isEqualTo("IMAGE:" + IMAGE_URL);
    }

    @Test
    @ActivateRequestContext
    void contentReturningToolIsSentAsThatContent() {
        assertThat(aiService.chat("contentTool")).isEqualTo("IMAGE:" + IMAGE_URL);
    }

    @Test
    @ActivateRequestContext
    void contentCollectionReturningToolIsSentAsThoseContents() {
        assertThat(aiService.chat("contentListTool")).isEqualTo("TEXT:a cat|IMAGE:" + IMAGE_URL);
    }

    @Test
    @ActivateRequestContext
    void contentArrayReturningToolIsSentAsThoseContents() {
        assertThat(aiService.chat("contentArrayTool")).isEqualTo("TEXT:a cat|IMAGE:" + IMAGE_URL);
    }

    /**
     * Guards the fallback: anything that is not content-shaped must still be JSON-serialized into the tool
     * result text.
     */
    @Test
    @ActivateRequestContext
    void nonContentReturningToolIsStillSentAsJsonText() {
        assertThat(aiService.chat("pojoTool")).startsWith("TEXT:")
                .contains("\"name\":\"cat\"")
                .contains("\"legs\":4");
    }

    /**
     * An empty collection is not content-shaped either — upstream only inspects a non-empty one — so it must
     * keep falling back to JSON text rather than building an invalid empty content list.
     */
    @Test
    @ActivateRequestContext
    void emptyCollectionReturningToolIsStillSentAsJsonText() {
        assertThat(aiService.chat("emptyListTool")).isEqualTo("TEXT:[]");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, tools = MyTools.class)
    public interface MyAiService {
        String chat(@UserMessage String toolName);
    }

    public record Animal(String name, int legs) {
    }

    @ApplicationScoped
    public static class MyTools {

        @Tool
        public Image imageTool() {
            return Image.builder().url(IMAGE_URL).build();
        }

        @Tool
        public Content contentTool() {
            return ImageContent.from(IMAGE_URL);
        }

        @Tool
        public List<Content> contentListTool() {
            return List.of(TextContent.from("a cat"), ImageContent.from(IMAGE_URL));
        }

        @Tool
        public Content[] contentArrayTool() {
            return new Content[] { TextContent.from("a cat"), ImageContent.from(IMAGE_URL) };
        }

        @Tool
        public Animal pojoTool() {
            return new Animal("cat", 4);
        }

        @Tool
        public List<Content> emptyListTool() {
            return List.of();
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                String toolName = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(
                                ToolExecutionRequest.builder()
                                        .id("tool-" + toolName)
                                        .name(toolName)
                                        .arguments("{}")
                                        .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(describe(toolResult.contents())))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            throw new RuntimeException("Unexpected number of messages: " + messages.size());
        }

        private static String describe(List<Content> contents) {
            return contents.stream().map(MyChatModel::describe).collect(Collectors.joining("|"));
        }

        private static String describe(Content content) {
            if (content instanceof TextContent text) {
                return "TEXT:" + text.text();
            }
            if (content instanceof ImageContent image) {
                return "IMAGE:" + image.image().url();
            }
            return content.type().name();
        }
    }
}

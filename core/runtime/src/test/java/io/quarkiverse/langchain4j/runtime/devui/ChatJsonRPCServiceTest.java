package io.quarkiverse.langchain4j.runtime.devui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import io.quarkiverse.langchain4j.runtime.devui.json.ChatResultPojo;

class ChatJsonRPCServiceTest {

    @Test
    void imageIsSentToTheModel() {
        RecordingChatModel model = new RecordingChatModel();
        ChatJsonRPCService service = service(model, List.of());

        ChatResultPojo result = service.chat("Describe this", false, "aGVsbG8=", "image/png");

        assertNull(result.getError());
        UserMessage sent = lastUserMessage(model);
        assertEquals(2, sent.contents().size());
        assertEquals("Describe this", ((TextContent) sent.contents().get(0)).text());
        ImageContent image = assertInstanceOf(ImageContent.class, sent.contents().get(1));
        assertEquals("aGVsbG8=", image.image().base64Data());
        assertEquals("image/png", image.image().mimeType());
    }

    @Test
    void onlyTextIsSentWithoutImage() {
        RecordingChatModel model = new RecordingChatModel();
        ChatJsonRPCService service = service(model, List.of());

        service.chat("Hello", false, null, null);
        UserMessage first = lastUserMessage(model);
        service.chat("Hello again", false, "", "image/png");
        UserMessage second = lastUserMessage(model);

        assertEquals(1, first.contents().size());
        assertEquals("Hello", first.singleText());
        assertEquals(1, second.contents().size());
        assertEquals("Hello again", second.singleText());
    }

    @Test
    void imageIsAddedToTheRagAugmentedMessageAndTheAugmentorGetsOnlyText() {
        RecordingChatModel model = new RecordingChatModel();
        List<ChatMessage> augmentorInputs = new ArrayList<>();
        RetrievalAugmentor augmentor = new RetrievalAugmentor() {
            @Override
            public AugmentationResult augment(AugmentationRequest request) {
                augmentorInputs.add(request.chatMessage());
                UserMessage original = (UserMessage) request.chatMessage();
                UserMessage augmented = UserMessage.builder()
                        .name("rag-user")
                        .contents(List.of(TextContent.from(original.singleText() + "\n\nSome context.")))
                        .attributes(Map.of("source", "test"))
                        .build();
                return AugmentationResult.builder().chatMessage(augmented).build();
            }
        };
        ChatJsonRPCService service = service(model, List.of(augmentor));

        ChatResultPojo result = service.chat("Describe this", true, "aGVsbG8=", "image/jpeg");

        assertNull(result.getError());
        assertEquals(1, augmentorInputs.size());
        assertEquals("Describe this", ((UserMessage) augmentorInputs.get(0)).singleText());

        UserMessage sent = lastUserMessage(model);
        assertEquals("rag-user", sent.name());
        assertEquals(Map.of("source", "test"), sent.attributes());
        assertEquals(2, sent.contents().size());
        assertEquals("Describe this\n\nSome context.", ((TextContent) sent.contents().get(0)).text());
        ImageContent image = assertInstanceOf(ImageContent.class, sent.contents().get(1));
        assertEquals("aGVsbG8=", image.image().base64Data());
        assertEquals("image/jpeg", image.image().mimeType());
    }

    private static ChatJsonRPCService service(ChatModel model, List<RetrievalAugmentor> augmentors) {
        ChatMemoryProvider memoryProvider = new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                return MessageWindowChatMemory.builder().id(memoryId).maxMessages(10).build();
            }
        };
        return new ChatJsonRPCService(List.of(model), List.of(), List.of(), augmentors, memoryProvider, null,
                List.of(), List.of());
    }

    private static UserMessage lastUserMessage(RecordingChatModel model) {
        List<ChatMessage> messages = model.requests.get(model.requests.size() - 1).messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage;
            }
        }
        throw new AssertionError("No user message was sent to the model");
    }

    private static class RecordingChatModel implements ChatModel {

        final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }
}

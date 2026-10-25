package io.quarkiverse.langchain4j.test.augmentation;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;

public class AugmentWithTestUtils {

    public static class EchoChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new EchoChatModel();
        }
    }

    public static class EchoChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            ChatMessage last = request.messages().get(request.messages().size() - 1);
            return ChatResponse.builder().aiMessage(new AiMessage(((UserMessage) last).singleText())).build();
        }
    }

    public static class EchoStreamedChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new EchoStreamedChatModel();
        }
    }

    public static class EchoStreamedChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            ChatMessage last = chatRequest.messages().get(chatRequest.messages().size() - 1);
            handler.onPartialResponse(((UserMessage) last).singleText());
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
        }
    }

    public static class AppendingAugmentor implements RetrievalAugmentor {

        private final String suffix;
        private final AtomicInteger invocations;

        public AppendingAugmentor(String suffix, AtomicInteger invocations) {
            this.suffix = suffix;
            this.invocations = invocations;
        }

        @Override
        public AugmentationResult augment(AugmentationRequest augmentationRequest) {
            invocations.incrementAndGet();
            String text = ((UserMessage) augmentationRequest.chatMessage()).singleText();
            return new AugmentationResult(UserMessage.userMessage(text + suffix), List.of());
        }
    }

    public static class ClassLevelSupplier implements Supplier<RetrievalAugmentor> {

        public static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Override
        public RetrievalAugmentor get() {
            return new AppendingAugmentor("|class-level", INVOCATIONS);
        }
    }

    public static class FirstMethodSupplier implements Supplier<RetrievalAugmentor> {

        public static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Override
        public RetrievalAugmentor get() {
            return new AppendingAugmentor("|first-method", INVOCATIONS);
        }
    }

    public static class SecondMethodSupplier implements Supplier<RetrievalAugmentor> {

        public static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Override
        public RetrievalAugmentor get() {
            return new AppendingAugmentor("|second-method", INVOCATIONS);
        }
    }

    @ApplicationScoped
    public static class CdiBeanSupplier implements Supplier<RetrievalAugmentor> {

        public static final AtomicInteger INVOCATIONS = new AtomicInteger();

        private String suffix;

        @PostConstruct
        void init() {
            suffix = "|cdi-bean";
        }

        @Override
        public RetrievalAugmentor get() {
            return new AppendingAugmentor(suffix, INVOCATIONS);
        }
    }
}

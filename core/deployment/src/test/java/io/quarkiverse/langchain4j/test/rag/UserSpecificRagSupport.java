package io.quarkiverse.langchain4j.test.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
import dev.langchain4j.rag.content.Content;
import io.quarkus.security.identity.SecurityIdentity;

public final class UserSpecificRagSupport {

    public static final String ALICE_DOCUMENT = "Alice's private document: the launch passcode is ALPHA-1.";
    public static final String BOB_DOCUMENT = "Bob's private document: the launch passcode is BRAVO-2.";

    private UserSpecificRagSupport() {
    }

    @ApplicationScoped
    public static class UserDocuments {

        private final Map<String, List<String>> documentsByUser = new ConcurrentHashMap<>();

        @PostConstruct
        void seed() {
            add("alice", ALICE_DOCUMENT);
            add("bob", BOB_DOCUMENT);
        }

        public void add(String username, String document) {
            documentsByUser.putIfAbsent(username, new CopyOnWriteArrayList<>());
            documentsByUser.get(username).add(document);
        }

        public List<String> documents(String username) {
            return documentsByUser.getOrDefault(username, List.of());
        }
    }

    @ApplicationScoped
    public static class UserAugmentorSupplier implements Supplier<RetrievalAugmentor> {

        public static final AtomicInteger AUGMENTOR_CREATIONS = new AtomicInteger();
        public static final AtomicReference<String> LAST_USER = new AtomicReference<>();
        public static final AtomicReference<String> LAST_METHOD = new AtomicReference<>();
        public static final AtomicReference<String> LAST_INTERFACE = new AtomicReference<>();
        public static final AtomicReference<Object> LAST_MEMORY_ID = new AtomicReference<>();
        public static final AtomicReference<List<Object>> LAST_METHOD_ARGUMENTS = new AtomicReference<>();

        @Inject
        SecurityIdentity identity;

        @Inject
        UserDocuments documents;

        @Override
        public RetrievalAugmentor get() {
            AUGMENTOR_CREATIONS.incrementAndGet();
            return new RetrievalAugmentor() {
                @Override
                public AugmentationResult augment(AugmentationRequest request) {
                    String username = identity.getPrincipal().getName();
                    List<Content> contents = new ArrayList<>();
                    for (String document : documents.documents(username)) {
                        contents.add(Content.from(document));
                    }

                    var invocationContext = request.metadata().invocationContext();
                    String methodName = invocationContext.methodName();
                    LAST_USER.set(username);
                    LAST_METHOD.set(methodName);
                    LAST_INTERFACE.set(invocationContext.interfaceName());
                    LAST_MEMORY_ID.set(invocationContext.chatMemoryId());
                    LAST_METHOD_ARGUMENTS.set(invocationContext.methodArguments());

                    String prompt = "summarize".equals(methodName)
                            ? "Answer briefly, using only the documents below."
                            : "Answer using only the documents below.";
                    StringBuilder augmented = new StringBuilder(((UserMessage) request.chatMessage()).singleText());
                    augmented.append("\n\n").append(prompt).append("\n");
                    for (Content content : contents) {
                        augmented.append("- ").append(content.textSegment().text()).append("\n");
                    }
                    return new AugmentationResult(UserMessage.from(augmented.toString()), contents);
                }
            };
        }
    }

    public static class EchoModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new EchoModel();
        }
    }

    public static class EchoStreamingModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new EchoStreamingModel();
        }
    }

    public static class EchoModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from(allUserMessages(chatRequest))).build();
        }
    }

    public static class EchoStreamingModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            String text = allUserMessages(chatRequest);
            handler.onPartialResponse(text);
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(text)).build());
        }
    }

    static String allUserMessages(ChatRequest chatRequest) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : chatRequest.messages()) {
            if (message instanceof UserMessage userMessage) {
                text.append(userMessage.singleText()).append('\n');
            }
        }
        return text.toString();
    }
}

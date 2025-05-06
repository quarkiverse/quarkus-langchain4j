package io.quarkiverse.langchain4j.test.streaming;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkus.arc.Arc;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Vertx;

/**
 * Utility class for streaming tests.
 */
public class StreamTestUtils {

    public static class FakeMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new MessageWindowChatMemory.Builder()
                            .id(memoryId)
                            .maxMessages(10)
                            .chatMemoryStore(new FakeMemoryStore())
                            .build();
                }
            };
        }
    }

    public static class FakeStreamedChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new FakeStreamedChatModel();
        }
    }

    public static class FakeStreamedChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            Vertx vertx = Arc.container().select(Vertx.class).get();
            var ctxt = vertx.getOrCreateContext();

            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() > 1) {
                var last = (UserMessage) messages.get(messages.size() - 1);
                if (last.singleText().equalsIgnoreCase("Second message")) {
                    if (messages.size() < 3) {
                        ctxt.runOnContext(x -> handler.onError(new IllegalStateException("Error - no memory")));
                        return;
                    } else {
                        ctxt.runOnContext(x -> {
                            handler.onPartialResponse("OK!");
                            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
                        });
                        return;
                    }
                }
            }

            ctxt.runOnContext(x1 -> {
                handler.onPartialResponse("Hi!");
                ctxt.runOnContext(x2 -> {
                    handler.onPartialResponse(" ");
                    ctxt.runOnContext(x3 -> {
                        handler.onPartialResponse("World!");
                        ctxt.runOnContext(
                                x -> handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build()));
                    });
                });
            });
        }
    }

    public static class FakeMemoryStore implements ChatMemoryStore {

        public static String DC_DATA;

        private static final Map<Object, List<ChatMessage>> memories = new ConcurrentHashMap<>();

        private void checkDuplicatedContext() {
            if (DC_DATA != null) {
                if (!DC_DATA.equals(Vertx.currentContext().getLocal("DC_DATA"))) {
                    throw new AssertionError("Expected to be in the same context");
                }
            }
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            if (!Infrastructure.canCallerThreadBeBlocked()) {
                throw new AssertionError("Expected to be able to block");
            }
            checkDuplicatedContext();
            return memories.computeIfAbsent(memoryId, x -> new CopyOnWriteArrayList<>());
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            if (!Infrastructure.canCallerThreadBeBlocked()) {
                throw new AssertionError("Expected to be able to block");
            }
            memories.put(memoryId, messages);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            if (!Infrastructure.canCallerThreadBeBlocked()) {
                throw new AssertionError("Expected to be able to block");
            }
        }
    }
}

package io.quarkiverse.langchain4j.runtime.aiservice;

import org.jboss.logging.Logger;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.ChatHistoryStore;
import io.smallrye.mutiny.Multi;

public class ChatHistorySupport {

    private static final Logger log = Logger.getLogger(ChatHistorySupport.class);

    private ChatHistorySupport() {
        // Avoid direct instantiation
    }

    public static void recordUserMessage(ChatHistoryStore store, Object memoryId, UserMessage userMessage) {
        safeInvoke(memoryId, "user message", () -> store.onUserMessage(memoryId, extractText(userMessage)));
    }

    public static void recordAgentResponse(ChatHistoryStore store, Object memoryId, String agentText) {
        safeInvoke(memoryId, "agent message", () -> {
            store.onAgentMessage(memoryId, agentText);
            store.onCompleted(memoryId);
        });
    }

    public static Multi<?> attach(Multi<?> stream, ChatHistoryStore store, Object memoryId, boolean isStringMulti) {
        StringBuilder buffer = new StringBuilder();
        return stream
                .onItem().invoke(item -> recordPartial(store, memoryId, item, isStringMulti, buffer))
                .onCompletion().invoke(() -> recordAgentResponse(store, memoryId, buffer.toString()))
                .onFailure().invoke(error -> recordFailure(store, memoryId, buffer, error))
                .onCancellation().invoke(() -> recordCancellation(store, memoryId, buffer));
    }

    private static void recordPartial(ChatHistoryStore store, Object memoryId, Object item,
            boolean isStringMulti, StringBuilder buffer) {
        String chunk = extractChunk(item, isStringMulti);
        if (chunk == null) {
            return;
        }
        buffer.append(chunk);
        safeInvoke(memoryId, "partial agent message", () -> store.onAgentPartial(memoryId, chunk));
    }

    private static void recordFailure(ChatHistoryStore store, Object memoryId, StringBuilder buffer, Throwable error) {
        safeInvoke(memoryId, "streaming failure", () -> store.onError(memoryId, error, buffer.toString()));
    }

    private static void recordCancellation(ChatHistoryStore store, Object memoryId, StringBuilder buffer) {
        safeInvoke(memoryId, "streaming cancellation", () -> store.onCancelled(memoryId, buffer.toString()));
    }

    private static String extractChunk(Object item, boolean isStringMulti) {
        if (isStringMulti) {
            return (String) item;
        }
        if (item instanceof ChatEvent.PartialResponseEvent pre) {
            return pre.getChunk();
        }
        return null;
    }

    private static String extractText(UserMessage userMessage) {
        StringBuilder sb = new StringBuilder();
        for (Content content : userMessage.contents()) {
            if (content instanceof TextContent text) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

    private static void safeInvoke(Object memoryId, String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warnf(e, "Failed to record %s for memoryId=%s, continuing without recording.", operation, memoryId);
        }
    }
}

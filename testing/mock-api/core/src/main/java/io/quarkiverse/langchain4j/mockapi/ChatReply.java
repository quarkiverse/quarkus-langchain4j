package io.quarkiverse.langchain4j.mockapi;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.internal.model.ChatReplyImpl;

/**
 * A reply to a chat request. Instances are immutable: every fluent method returns a new instance.
 */
public non-sealed interface ChatReply extends Reply {

    static ChatReply text(String text) {
        return new ChatReplyImpl(text, null, null, null, null, null, null, null, null);
    }

    static ChatReply toolCall(String name, Map<String, Object> args) {
        return ChatReplyImpl.EMPTY.addToolCall(name, args);
    }

    static ChatReply toolCall(String name, String argumentsJson) {
        return ChatReplyImpl.EMPTY.addToolCall(name, argumentsJson);
    }

    ChatReply addToolCall(String name, Map<String, Object> args);

    ChatReply addToolCall(String name, String argumentsJson);

    ChatReply usage(int in, int out);

    ChatReply finishReason(FinishReason finishReason);

    ChatReply thinking(String thinking);

    ChatReply modelName(String modelName);

    ChatReply id(String id);

    ChatReply delay(Duration delay);

    ChatReply chunks(List<String> chunks);

    String text();

    List<ToolCall> toolCalls();

    Usage usage();

    /**
     * The configured finish reason, or {@link FinishReason#TOOL_CALLS} when there are tool calls and
     * {@link FinishReason#STOP} otherwise.
     */
    FinishReason finishReason();

    String thinking();

    String modelName();

    String id();

    List<String> chunks();
}

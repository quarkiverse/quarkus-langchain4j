package io.quarkiverse.langchain4j.memory;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import io.smallrye.common.annotation.Experimental;

/**
 * A {@link ChatMemory} that stores a full history but sends a different <em>view</em> of it to the model, for example a
 * compacted one.
 * <p>
 * For such a memory, {@link #messages()} returns the view of the stored history, so it cannot be used to rebuild the
 * history. Quarkus LangChain4j recognizes this interface and, during an AI service invocation:
 * <ul>
 * <li>buffers the full {@link #history() history} (not the view) together with the messages added during the
 * invocation (user message, tool calls and results, answer);</li>
 * <li>computes the {@link #view(List) view} of that buffer each time it builds a request to the model, so the messages
 * added by a long tool loop are subject to the view as well;</li>
 * <li>writes the buffer back with {@link #setHistory(List)} instead of {@link #set(Iterable)}.</li>
 * </ul>
 */
@Experimental("This API is subject to change")
public interface HistoryViewChatMemory extends ChatMemory {

    /**
     * @return a mutable copy of the full stored history, in chronological order
     */
    List<ChatMessage> history();

    /**
     * Computes the messages to send to the model for the given history.
     * <p>
     * The history can contain messages that are not stored yet (the messages buffered during an invocation). This method
     * must not modify its argument nor the stored history.
     *
     * @param history the full history, in chronological order
     * @return the messages to send to the model
     */
    List<ChatMessage> view(List<ChatMessage> history);

    /**
     * Replaces the stored history. Unlike {@link #set(Iterable)}, the argument is a full history, never a view.
     *
     * @param history the new full history
     */
    void setHistory(List<ChatMessage> history);
}

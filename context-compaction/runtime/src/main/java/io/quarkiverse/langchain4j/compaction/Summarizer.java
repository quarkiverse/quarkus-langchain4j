package io.quarkiverse.langchain4j.compaction;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Produces a summary of a span of conversation, used by the summarizing strategies.
 * <p>
 * The default implementation calls a chat model (a cheaper model can be selected with
 * {@code quarkus.langchain4j.compaction.summary.model-name}). Applications can replace it by declaring their own bean.
 */
public interface Summarizer {

    /**
     * Summarizes {@code messages}, folding in {@code previousSummary} when it is not {@code null}.
     *
     * @param previousSummary the summary of the span that precedes {@code messages}, or {@code null}
     * @param messages the messages to summarize, in chronological order
     * @param maxWords soft limit for the summary length
     * @return the new summary
     */
    String summarize(String previousSummary, List<ChatMessage> messages, int maxWords);
}

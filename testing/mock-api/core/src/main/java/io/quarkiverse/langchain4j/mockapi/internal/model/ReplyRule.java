package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.util.List;

import io.quarkiverse.langchain4j.mockapi.Reply;

/**
 * Replies for the chat requests whose last user message contains a text, as sent to the admin API. The replies are
 * sent in order, one per matching request, the last one repeating.
 */
public record ReplyRule(String userMessageContains, List<Reply> replies) {
}

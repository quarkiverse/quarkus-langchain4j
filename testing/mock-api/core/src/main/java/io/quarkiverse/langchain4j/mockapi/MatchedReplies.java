package io.quarkiverse.langchain4j.mockapi;

import java.util.List;

import io.quarkiverse.langchain4j.mockapi.internal.AdminClient;
import io.quarkiverse.langchain4j.mockapi.internal.model.ReplyRule;

/**
 * Replies for the chat requests whose last user message contains a text, see
 * {@link OperationScope#whenUserMessageContains(String)}.
 */
public final class MatchedReplies {

    private final AdminClient admin;
    private final String userMessageContains;

    MatchedReplies(AdminClient admin, String userMessageContains) {
        this.admin = admin;
        this.userMessageContains = userMessageContains;
    }

    /**
     * Answers every matching request with this text.
     *
     * @param text the text of the reply
     */
    public void reply(String text) {
        replies(ChatReply.text(text));
    }

    /**
     * Sends the replies in order, one per matching request; the last one repeats.
     *
     * @param inOrder the replies, at least one
     */
    public void replies(Reply... inOrder) {
        if (inOrder.length == 0) {
            throw new IllegalArgumentException("at least one reply is needed");
        }
        admin.postJson("/_admin/rules/chat", new ReplyRule(userMessageContains, List.of(inOrder)));
    }

    /**
     * Answers every matching request with an error in the provider's format.
     *
     * @param status the HTTP status
     * @param message the error message
     */
    public void fail(int status, String message) {
        replies(ErrorReply.of(status, message));
    }
}

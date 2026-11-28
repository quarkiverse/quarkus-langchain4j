package io.quarkiverse.langchain4j.mockapi;

import java.util.List;

import io.quarkiverse.langchain4j.mockapi.internal.AdminClient;

/**
 * Replies and recorded requests for one {@link Operation}.
 */
public final class OperationScope {

    private final AdminClient admin;
    private final Operation operation;

    OperationScope(AdminClient admin, Operation operation) {
        this.admin = admin;
        this.operation = operation;
    }

    public void reply(String text) {
        if (operation != Operation.CHAT) {
            throw new IllegalStateException("reply(String) is only supported for CHAT, not " + operation);
        }
        reply(ChatReply.text(text));
    }

    public void reply(Reply... replies) {
        replies(List.of(replies));
    }

    public void replies(List<Reply> replies) {
        // an array keeps the Reply element type, so the "kind" discriminator is written (a List<Reply> would erase it)
        admin.postJson("/_admin/replies/" + path(), replies.toArray(new Reply[0]));
    }

    /**
     * Replies for the chat requests whose last user message contains {@code text}, whatever their order. They take
     * precedence over the replies queued with {@link #reply(Reply...)} and over the default reply, and stay until
     * {@link MockModelServer#reset()}. When several match, the first one added wins.
     *
     * @param text the text the last user message must contain, case-sensitive
     * @return where to give the replies
     */
    public MatchedReplies whenUserMessageContains(String text) {
        if (operation != Operation.CHAT) {
            throw new IllegalStateException("whenUserMessageContains is only supported for CHAT, not " + operation);
        }
        return new MatchedReplies(admin, text);
    }

    public void fail(int status, String message) {
        reply(ErrorReply.of(status, message));
    }

    public void defaultReply(Reply reply) {
        admin.putJson("/_admin/replies/" + path() + "/default", reply);
    }

    public List<CapturedRequest> requests() {
        return MockModelServer.parseRequests(admin.get("/_admin/requests?operation=" + path()));
    }

    public CapturedRequest singleRequest() {
        List<CapturedRequest> requests = requests();
        if (requests.size() != 1) {
            throw new AssertionError("expected exactly 1 " + operation + " request but found " + requests.size());
        }
        return requests.get(0);
    }

    private String path() {
        return operation.name().toLowerCase();
    }
}

package io.quarkiverse.langchain4j.mockapi.internal.dialect;

import io.quarkiverse.langchain4j.mockapi.Reply;

/**
 * Thrown by {@link Dialect#render} for a reply the provider's format cannot express: a reply of the wrong type for the
 * operation, too few scripted vectors or scores, tool call arguments of the wrong shape. The server answers it with
 * {@link Dialect#renderError} and status 500, with this exception's message.
 */
public final class UnrenderableReply extends RuntimeException {

    public UnrenderableReply(String message) {
        super(message);
    }

    /**
     * A reply whose type the dialect does not render for the matched operation.
     *
     * @param format the wire format as named in the message, for example {@code Mistral}
     */
    public static UnrenderableReply unsupported(String format, Dialect.Match m, Reply reply) {
        return new UnrenderableReply(
                format + " render not supported: " + m.operation() + " with " + reply.getClass().getSimpleName());
    }
}

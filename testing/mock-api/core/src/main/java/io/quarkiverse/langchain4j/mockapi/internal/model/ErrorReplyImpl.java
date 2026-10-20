package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.time.Duration;

import io.quarkiverse.langchain4j.mockapi.ErrorReply;

public record ErrorReplyImpl(int status, String message, Duration delay) implements ErrorReply {

    @Override
    public ErrorReply delay(Duration delay) {
        return new ErrorReplyImpl(status, message, delay);
    }
}

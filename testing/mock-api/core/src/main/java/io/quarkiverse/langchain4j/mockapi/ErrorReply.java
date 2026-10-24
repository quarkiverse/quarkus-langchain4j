package io.quarkiverse.langchain4j.mockapi;

import java.time.Duration;

import io.quarkiverse.langchain4j.mockapi.internal.model.ErrorReplyImpl;

public non-sealed interface ErrorReply extends Reply {

    static ErrorReply of(int status, String message) {
        return new ErrorReplyImpl(status, message, null);
    }

    int status();

    String message();

    ErrorReply delay(Duration delay);
}

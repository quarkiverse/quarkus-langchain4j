package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.time.Duration;
import java.util.List;

import io.quarkiverse.langchain4j.mockapi.ModerationReply;

public record ModerationReplyImpl(boolean flagged, List<String> categories, Duration delay) implements ModerationReply {

    @Override
    public ModerationReply delay(Duration delay) {
        return new ModerationReplyImpl(flagged, categories, delay);
    }
}

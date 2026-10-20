package io.quarkiverse.langchain4j.mockapi;

import java.time.Duration;

public sealed interface Reply permits ChatReply, EmbeddingReply, ModerationReply, ScoringReply, DecisionReply, ErrorReply,
        RawResponse {

    Duration delay();
}

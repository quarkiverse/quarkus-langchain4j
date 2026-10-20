package io.quarkiverse.langchain4j.mockapi.internal.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.quarkiverse.langchain4j.mockapi.internal.model.ChatReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.DecisionReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.EmbeddingReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ErrorReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ModerationReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.RawResponseImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ScoringReplyImpl;

/**
 * The {@code kind} discriminator of a reply on the admin API, under the public type names.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatReplyImpl.class, name = "ChatReply"),
        @JsonSubTypes.Type(value = EmbeddingReplyImpl.class, name = "EmbeddingReply"),
        @JsonSubTypes.Type(value = ModerationReplyImpl.class, name = "ModerationReply"),
        @JsonSubTypes.Type(value = ScoringReplyImpl.class, name = "ScoringReply"),
        @JsonSubTypes.Type(value = DecisionReplyImpl.class, name = "DecisionReply"),
        @JsonSubTypes.Type(value = ErrorReplyImpl.class, name = "ErrorReply"),
        @JsonSubTypes.Type(value = RawResponseImpl.class, name = "RawResponse")
})
abstract class ReplyMixin {
}

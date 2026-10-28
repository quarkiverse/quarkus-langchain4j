package io.quarkiverse.langchain4j.mockapi.internal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.ErrorReply;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.Usage;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChoiceAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.DecisionReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.DecisionRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.DeclinedAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.EmbeddingReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ErrorReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.LevelImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.MessageImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ModerationReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.PredicateAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.QuestionImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.RawResponseImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ScoreAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.ScoringReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.SseEventImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ToolCallImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ToolDefinitionImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.UsageImpl;

/**
 * The one mapper the admin API, the admin client and the dialects share. The public API types carry no Jackson
 * annotations: every interface is mapped to its implementation here, and the two polymorphic roots ({@link Reply} and
 * {@link DecisionReply.Answer}) get their type discriminators through the mix-ins of this package.
 */
public final class MockApiJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(apiTypes())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    private MockApiJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static byte[] toBytes(JsonNode node) {
        try {
            return MAPPER.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SimpleModule apiTypes() {
        SimpleModule module = new SimpleModule("quarkus-langchain4j-mock-api");
        module.setMixInAnnotation(Reply.class, ReplyMixin.class);
        module.setMixInAnnotation(DecisionReply.Answer.class, AnswerMixin.class);
        module.setMixInAnnotation(ChatReplyImpl.class, ChatReplyMixin.class);
        module.addAbstractTypeMapping(ChatReply.class, ChatReplyImpl.class);
        module.addAbstractTypeMapping(EmbeddingReply.class, EmbeddingReplyImpl.class);
        module.addAbstractTypeMapping(ModerationReply.class, ModerationReplyImpl.class);
        module.addAbstractTypeMapping(ScoringReply.class, ScoringReplyImpl.class);
        module.addAbstractTypeMapping(DecisionReply.class, DecisionReplyImpl.class);
        module.addAbstractTypeMapping(DecisionReply.Predicate.class, PredicateAnswer.class);
        module.addAbstractTypeMapping(DecisionReply.Choice.class, ChoiceAnswer.class);
        module.addAbstractTypeMapping(DecisionReply.Score.class, ScoreAnswer.class);
        module.addAbstractTypeMapping(DecisionReply.Score.Level.class, LevelImpl.class);
        module.addAbstractTypeMapping(DecisionReply.Declined.class, DeclinedAnswer.class);
        module.addAbstractTypeMapping(ErrorReply.class, ErrorReplyImpl.class);
        module.addAbstractTypeMapping(RawResponse.class, RawResponseImpl.class);
        module.addAbstractTypeMapping(SseEvent.class, SseEventImpl.class);
        module.addAbstractTypeMapping(ToolCall.class, ToolCallImpl.class);
        module.addAbstractTypeMapping(Usage.class, UsageImpl.class);
        module.addAbstractTypeMapping(CapturedRequest.class, CapturedRequestImpl.class);
        module.addAbstractTypeMapping(ChatRequestView.class, ChatRequestViewImpl.class);
        module.addAbstractTypeMapping(ChatRequestView.Message.class, MessageImpl.class);
        module.addAbstractTypeMapping(ChatRequestView.ToolDefinition.class, ToolDefinitionImpl.class);
        module.addAbstractTypeMapping(DecisionRequestView.class, DecisionRequestViewImpl.class);
        module.addAbstractTypeMapping(DecisionRequestView.Question.class, QuestionImpl.class);
        return module;
    }
}

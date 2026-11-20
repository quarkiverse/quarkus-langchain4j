package io.quarkiverse.langchain4j.mockapi.internal.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.quarkiverse.langchain4j.mockapi.internal.model.ChoiceAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.DeclinedAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.PredicateAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.ScoreAnswer;

/**
 * The {@code type} discriminator of a decision answer.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PredicateAnswer.class, name = "predicate"),
        @JsonSubTypes.Type(value = ChoiceAnswer.class, name = "choice"),
        @JsonSubTypes.Type(value = ScoreAnswer.class, name = "score"),
        @JsonSubTypes.Type(value = DeclinedAnswer.class, name = "declined")
})
abstract class AnswerMixin {
}

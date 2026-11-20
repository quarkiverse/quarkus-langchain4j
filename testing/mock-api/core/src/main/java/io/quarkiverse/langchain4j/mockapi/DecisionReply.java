package io.quarkiverse.langchain4j.mockapi;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.internal.model.ChoiceAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.DecisionReplyImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.DeclinedAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.LevelImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.PredicateAnswer;
import io.quarkiverse.langchain4j.mockapi.internal.model.ScoreAnswer;

/**
 * A reply to a Decisions API request. An empty {@code answers} list answers every question with {@link Declined}.
 */
public non-sealed interface DecisionReply extends Reply {

    static DecisionReply of(Answer... answers) {
        return new DecisionReplyImpl(Arrays.asList(answers), null, null, null);
    }

    static Predicate predicate(String name, double probability) {
        return new PredicateAnswer(name, probability);
    }

    static Choice choice(String name, String choice, Map<String, Double> probabilities, Double confidence) {
        return new ChoiceAnswer(name, choice, probabilities, confidence);
    }

    static Score score(String name, double score, List<Score.Level> probabilities, Double confidence) {
        return new ScoreAnswer(name, score, probabilities, confidence);
    }

    static Score.Level level(int value, String label, double probability) {
        return new LevelImpl(value, label, probability);
    }

    static Declined declined(String name) {
        return new DeclinedAnswer(name);
    }

    List<Answer> answers();

    Usage usage();

    String modelName();

    DecisionReply usage(int in, int out);

    DecisionReply modelName(String modelName);

    DecisionReply delay(Duration delay);

    sealed interface Answer permits Predicate, Choice, Score, Declined {

        String name();
    }

    non-sealed interface Predicate extends Answer {

        double probability();
    }

    non-sealed interface Choice extends Answer {

        String choice();

        Map<String, Double> probabilities();

        Double confidence();
    }

    non-sealed interface Score extends Answer {

        double score();

        List<Level> probabilities();

        Double confidence();

        interface Level {

            int value();

            String label();

            double probability();
        }
    }

    non-sealed interface Declined extends Answer {
    }
}

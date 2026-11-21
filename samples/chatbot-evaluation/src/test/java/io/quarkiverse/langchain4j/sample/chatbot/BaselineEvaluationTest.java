package io.quarkiverse.langchain4j.sample.chatbot;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.ScorerConfiguration;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkiverse.langchain4j.testing.evaluation.judge.AiJudgeStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.similarity.SemanticSimilarityStrategy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Demonstrates comparing an evaluation with a committed baseline instead of an absolute threshold.
 * <p>
 * The first run fails because there is no baseline yet. Run {@code quarkus langchain4j-eval review} to accept the
 * result as the baseline and commit it. After that, the test fails only when a change makes the bot worse than it
 * was. See the README for a walkthrough.
 * </p>
 */
@QuarkusTest
@Evaluate
public class BaselineEvaluationTest {

    @Inject
    CustomerSupportBot bot;

    @Inject
    ChatModel judgeModel;

    @Test
    void customerSupportIsNotWorseThanBaseline(
            @ScorerConfiguration(concurrency = 3, trials = 2) Scorer scorer,
            @SampleLocation("src/test/resources/customer-support-samples.yaml") Samples<String> samples) {

        EvaluationReport<String> report = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                // Deterministic check: counts toward the score and the comparison
                new SemanticSimilarityStrategy(0.80),
                // AI judge: reported and compared, but never fails the test on its own
                EvaluationStrategy.toAdvisory(new AiJudgeStrategy(judgeModel)));

        // Fails only if the mean per-sample pass rate dropped by more than 5 points against the baseline
        assertThat(report).isNotWorseThanBaseline();
    }
}

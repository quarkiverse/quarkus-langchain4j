package io.quarkiverse.langchain4j.sample.chatbot;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import java.util.function.Function;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayNameGeneration;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationDisplayNameGenerator;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationFunction;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationTest;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.StrategyTest;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.Parameters;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkiverse.langchain4j.testing.evaluation.judge.AiJudgeStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.similarity.SemanticSimilarityStrategy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Demonstrates declarative evaluation testing using annotations.
 * This approach is more concise and reduces boilerplate code.
 */
@QuarkusTest
@Evaluate
@DisplayNameGeneration(EvaluationDisplayNameGenerator.class)
public class DeclarativeEvaluationTest {

    @Inject
    CustomerSupportBot bot;

    @Inject // Reuse the chat model for AI judging
    ChatModel judgeModel;

    /**
     * Define a reusable evaluation function.
     * This function will be referenced by name in the test annotations.
     */
    @EvaluationFunction("chatbot")
    public Function<Parameters, String> chatbotFunction() {
        return params -> bot.chat(params.get(0));
    }

    /**
     * Declarative test using @EvaluationTest.
     * The framework automatically loads samples, evaluates them,
     * and asserts the minimum score.
     */
    @EvaluationTest(samples = "smoke-tests.yaml", strategy = SemanticSimilarityStrategy.class, function = "chatbot", minScore = 70.0)
    void smokeTestsWithSemanticSimilarity() {
        // Test body can be empty - evaluation happens automatically
        // The test will fail if score is below 70%
    }

    /**
     * Test using multiple strategies with @StrategyTest.
     * The test runs once for each strategy.
     */
    @StrategyTest(strategies = {
            SemanticSimilarityStrategy.class,
            AiJudgeStrategy.class,
    })
    void customerSupportWithMultipleStrategies(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            EvaluationStrategy<String> strategy,
            Scorer scorer) {
        // This test method will execute twice:
        // 1. Once with SemanticSimilarityStrategy
        // 2. Once with AiJudgeStrategy
        // Each execution appears as a separate test in the results

        var report = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                strategy);

        System.out.printf("Strategy %s - Score: %.2f%%%n",
                strategy.getClass().getSimpleName(),
                report.score());

        assertThat(report).hasScoreGreaterThan(60.0);
    }

    /**
     * Another @EvaluationTest with different configuration.
     */
    @EvaluationTest(samples = "customer-support-samples.yaml", strategy = AiJudgeStrategy.class, function = "chatbot", minScore = 85.0)
    void criticalCustomerSupportEvaluation() {
        // Higher threshold for critical evaluations
    }
}

package io.quarkiverse.langchain4j.sample.chatbot;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.ScorerConfiguration;
import io.quarkiverse.langchain4j.mockapi.MockModelApiTestProfile;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkiverse.langchain4j.testing.evaluation.judge.AiJudgeStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.similarity.SemanticSimilarityStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The evaluation of {@link BaselineEvaluationTest} without OpenAI: the mock model API plays both the bot's model and
 * the judge, with scripted answers, so the baseline workflow can be tried without an API key, without credits and
 * with the same results on every run. The scorer and both strategies are the real ones, and the semantic similarity
 * runs on a local embedding model.
 * <p>
 * The bot answers every question with the sample's expected output, and the judge approves. Run with
 * {@code -Danswers=regressed} and the bot answers the business hours question badly, which the similarity strategy
 * and the judge both notice, as after a prompt change that lost the company information.
 * </p>
 */
@QuarkusTest
@TestProfile(MockModelApiTestProfile.class)
@Evaluate
public class OfflineBaselineEvaluationTest {

    static final String REGRESSED_SAMPLE = "Business Hours Query";
    static final String REGRESSED_ANSWER = "I'm not sure about our hours, please check the website.";

    @Inject
    CustomerSupportBot bot;

    @Inject
    ChatModel judgeModel;

    @Inject
    MockModelServer mock;

    @Test
    void customerSupportIsNotWorseThanBaseline(
            @ScorerConfiguration(concurrency = 3, trials = 2) Scorer scorer,
            @SampleLocation("src/test/resources/customer-support-samples.yaml") Samples<String> samples) {
        boolean regressed = "regressed".equals(System.getProperty("answers"));
        script(samples, regressed);

        EvaluationReport<String> report = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                new SemanticSimilarityStrategy(0.80),
                EvaluationStrategy.toAdvisory(new AiJudgeStrategy(judgeModel)));

        assertThat(report).isNotWorseThanBaseline();
    }

    /**
     * Replies are matched on the last user message, so they do not depend on the order in which the concurrent trials
     * reach the mock. The first matching rule wins, so the judge's rules, whose prompt quotes the answer, come first.
     */
    private void script(Samples<String> samples, boolean regressed) {
        mock.chat().whenUserMessageContains("Response to evaluate: " + REGRESSED_ANSWER).reply("false");
        mock.chat().whenUserMessageContains(AiJudgeStrategy.DEFAULT_PROMPT.lines().findFirst().orElseThrow())
                .reply("true");
        for (EvaluationSample<String> sample : samples) {
            String question = sample.parameters().get(0);
            boolean bad = regressed && sample.name().equals(REGRESSED_SAMPLE);
            mock.chat().whenUserMessageContains(question).reply(bad ? REGRESSED_ANSWER : sample.expectedOutput());
        }
    }
}

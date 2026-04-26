package io.quarkiverse.langchain4j.testing.evaluation.judge;

import jakarta.enterprise.inject.spi.CDI;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

/**
 * A strategy to evaluate the output of a model using an AI judge, _i.e._ another model verifying if the expected
 * output and the actual response match.
 */
public class AiJudgeStrategy implements EvaluationStrategy<String> {

    public static final String DEFAULT_PROMPT = """
            You are an AI evaluating a response and the expected output.
            You need to evaluate whether the model response is correct or not.
            Return true if the response is correct, false otherwise.

            Response to evaluate: {response}
            Expected output: {expected_output}

            """;
    private final ChatModel model;
    private final String prompt;

    /**
     * Create a new instance of `AiJudgeStrategy`.
     *
     * @param model the LLM model (chat model) to use as a judge.
     * @param prompt the prompt to use to evaluate the response.
     *        The prompt should contain the placeholders `{response}` and `{expected_output}`.
     */
    public AiJudgeStrategy(ChatModel model, String prompt) {
        this.model = model;
        this.prompt = prompt;
    }

    /**
     * Create a new instance of `AiJudgeStrategy` using the default prompt.
     *
     * @param model the LLM model (chat model) to use as a judge.
     */
    public AiJudgeStrategy(ChatModel model) {
        this(model, DEFAULT_PROMPT);
    }

    public AiJudgeStrategy() {
        try {
            this.model = CDI.current().select(ChatModel.class).get();
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                    "Failed to initialize AiJudgeStrategy using CDI to obtain ChatModel. " +
                            "Please use the constructor that accepts a ChatModel instance directly.",
                    e);
        }
        this.prompt = DEFAULT_PROMPT;
    }

    /**
     * Evaluate the output of a model using an AI judge.
     * <p>
     * Returns an evaluation result containing the judge's verdict,
     * the full explanation/reasoning from the judge model, and metadata
     * about which model was used as the judge.
     * </p>
     *
     * @param sample the sample to evaluate.
     * @param output the output of the model.
     * @return an evaluation result with judge's reasoning
     */
    @Override
    public EvaluationResult evaluate(
            EvaluationSample<String> sample, String output) {
        String expectedOutput = sample.expectedOutput();
        String prompt = this.prompt
                .replace("{response}", output)
                .replace("{expected_output}", expectedOutput);

        var verdict = model.chat(prompt);
        boolean passed = Boolean.parseBoolean(verdict.trim());

        return new EvaluationResult(
                passed,
                passed ? 1.0 : 0.0,
                verdict, // Full judge explanation
                java.util.Map.of("judge-model", model.getClass().getSimpleName()));
    }
}

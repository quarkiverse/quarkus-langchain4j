package io.quarkiverse.langchain4j.testing.scorer.judge;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.testing.scorer.EvaluationSample;
import io.quarkiverse.langchain4j.testing.scorer.EvaluationStrategy;

/**
 * A strategy to evaluate the output of a model using an AI judge, _i.e._ another model verifying if the expected
 * output and the actual response match.
 */
public class AiJudgeStrategy implements EvaluationStrategy<String> {

    private final ChatLanguageModel model;
    private final String prompt;

    /**
     * Create a new instance of `AiJudgeStrategy`.
     *
     * @param model the LLM model (chat model) to use as a judge.
     * @param prompt the prompt to use to evaluate the response.
     *        The prompt should contain the placeholders `{response}` and `{expected_output}`.
     */
    public AiJudgeStrategy(ChatLanguageModel model, String prompt) {
        this.model = model;
        this.prompt = prompt;
    }

    /**
     * Create a new instance of `AiJudgeStrategy` using the default prompt.
     *
     * @param model the LLM model (chat model) to use as a judge.
     */
    public AiJudgeStrategy(ChatLanguageModel model) {
        this(model, """
                You are an AI evaluating a response and the expected output.
                You need to evaluate whether the model response is correct or not.
                Return true if the response is correct, false otherwise.

                Response to evaluate: {response}
                Expected output: {expected_output}

                """);
    }

    /**
     * Evaluate the output of a model.
     *
     * @param sample the sample to evaluate.
     * @param output the output of the model.
     * @return {@code true} if the output is correct, {@code false} otherwise.
     */
    @Override
    public boolean evaluate(EvaluationSample<String> sample, String output) {
        String expectedOutput = sample.expectedOutput();
        String prompt = this.prompt
                .replace("{response}", output)
                .replace("{expected_output}", expectedOutput);
        var verdict = model.chat(prompt);
        return Boolean.parseBoolean(verdict.trim());
    }
}

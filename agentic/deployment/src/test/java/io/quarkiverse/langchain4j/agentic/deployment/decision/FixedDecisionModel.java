package io.quarkiverse.langchain4j.agentic.deployment.decision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;

/**
 * A decision model returning configured answers, keyed by question name, and recording the requests it receives.
 */
public class FixedDecisionModel implements DecisionModel {

    private final String modelName;
    private final Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private final List<DecisionRequest> requests = new CopyOnWriteArrayList<>();

    public FixedDecisionModel(String modelName) {
        this.modelName = modelName;
    }

    public FixedDecisionModel yesNo(String questionName, double probability) {
        answers.put(questionName, YesNoAnswer.from(probability));
        return this;
    }

    public FixedDecisionModel choice(String questionName, String value, Map<String, Double> probabilities) {
        answers.put(questionName, ChoiceAnswer.builder().value(value).probabilities(probabilities).build());
        return this;
    }

    public FixedDecisionModel scale(String questionName, double mean, List<Double> probabilities) {
        answers.put(questionName, ScaleAnswer.builder().mean(mean).probabilities(probabilities).build());
        return this;
    }

    /**
     * Makes the next calls fail, for instance to test a retry.
     */
    public FixedDecisionModel failing(int times) {
        failuresLeft.set(times);
        return this;
    }

    public List<DecisionRequest> requests() {
        return requests;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        requests.add(request);
        if (failuresLeft.getAndDecrement() > 0) {
            throw new IllegalStateException("The decision model is not available");
        }
        DecisionResponse.Builder response = DecisionResponse.builder()
                .modelName(modelName)
                .tokenUsage(new TokenUsage(10, 1));
        for (String questionName : request.questions().keySet()) {
            DecisionAnswer answer = answers.get(questionName);
            if (answer == null) {
                throw new IllegalStateException("No answer configured for question '" + questionName + "'");
            }
            response.answer(questionName, answer);
        }
        return response.build();
    }
}

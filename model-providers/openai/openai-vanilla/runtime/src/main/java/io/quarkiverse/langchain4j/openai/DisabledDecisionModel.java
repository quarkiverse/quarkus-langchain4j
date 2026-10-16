package io.quarkiverse.langchain4j.openai;

import java.util.concurrent.CompletableFuture;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;

public class DisabledDecisionModel implements DecisionModel {

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        throw disabled();
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        return CompletableFuture.failedFuture(disabled());
    }

    private static ModelDisabledException disabled() {
        return new ModelDisabledException("DecisionModel is disabled");
    }
}

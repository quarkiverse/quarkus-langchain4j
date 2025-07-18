package io.quarkiverse.langchain4j.cost;

import dev.langchain4j.model.output.TokenUsage;

/**
 * Allows for user code to handle estimate cost; e.g. some simple accounting
 */
public interface CostListener {
    void handleCost(String model, TokenUsage tokenUsage, Cost cost);

    default int order() {
        return 0;
    }
}

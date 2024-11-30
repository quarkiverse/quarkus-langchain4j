package io.quarkiverse.langchain4j.cost;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Singleton;

import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.arc.All;
import io.smallrye.common.annotation.Experimental;

/**
 * Meant to be injected where {@link dev.langchain4j.model.chat.listener.ChatModelListener}
 * is used in order to determine the cost of the API request
 */
@Singleton
@Experimental("This feature is experimental and the API is subject to change")
public class CostEstimatorService {

    private final List<CostEstimator> costEstimators;

    public CostEstimatorService(@All List<CostEstimator> costEstimators) {
        this.costEstimators = costEstimators != null ? Collections.unmodifiableList(costEstimators) : Collections.emptyList();
    }

    public Cost estimate(ChatModelResponseContext response) {
        TokenUsage tokenUsage = response.response().tokenUsage();
        CostEstimator.CostContext costContext = new MyCostContext(tokenUsage, response);
        return estimate(costContext);
    }

    public Cost estimate(CostEstimator.CostContext context) {
        for (CostEstimator costEstimator : costEstimators) {
            if (costEstimator.supports(context)) {
                CostEstimator.CostResult costResult = costEstimator.estimate(context);
                if (costResult != null) {
                    BigDecimal totalCost = costResult.inputTokensCost().add(costResult.outputTokensCost());
                    return new Cost(totalCost, costResult.currency());
                }
            }
        }
        return null;
    }

    private record MyCostContext(TokenUsage tokenUsage, ChatModelResponseContext response)
            implements
                CostEstimator.CostContext {

        @Override
        public Integer inputTokens() {
            return tokenUsage != null ? tokenUsage.inputTokenCount() : 0;
        }

        @Override
        public Integer outputTokens() {
            return tokenUsage != null ? tokenUsage.outputTokenCount() : 0;
        }

        @Override
        public String model() {
            return response.request().model();
        }
    }
}

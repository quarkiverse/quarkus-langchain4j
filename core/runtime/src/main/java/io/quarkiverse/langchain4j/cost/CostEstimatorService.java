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
        CostEstimator.CostContext costContext = new MyCostContext(response);

        for (CostEstimator costEstimator : costEstimators) {
            if (costEstimator.supports(costContext)) {
                CostEstimator.CostResult costResult = costEstimator.estimate(costContext);
                if (costResult != null) {
                    return new Cost(totalCost(costResult), costResult.currency());
                }
            }
        }
        return null;
    }

    private static BigDecimal totalCost(CostEstimator.CostResult costResult) {
        BigDecimal total = costResult.inputTokensCost().add(costResult.outputTokensCost());
        if (costResult.cacheReadTokensCost() != null) {
            total = total.add(costResult.cacheReadTokensCost());
        }
        if (costResult.cacheCreationTokensCost() != null) {
            total = total.add(costResult.cacheCreationTokensCost());
        }
        return total;
    }

    private record MyCostContext(ChatModelResponseContext responseContext) implements CostEstimator.CostContext {

        @Override
        public Integer inputTokens() {
            TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();
            return tokenUsage != null ? tokenUsage.inputTokenCount() : 0;
        }

        @Override
        public Integer outputTokens() {
            TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();
            return tokenUsage != null ? tokenUsage.outputTokenCount() : 0;
        }

        @Override
        public String model() {
            return responseContext.chatRequest().parameters().modelName();
        }
    }
}

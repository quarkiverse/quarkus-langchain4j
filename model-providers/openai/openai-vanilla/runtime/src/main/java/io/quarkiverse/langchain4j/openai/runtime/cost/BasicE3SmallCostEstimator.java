package io.quarkiverse.langchain4j.openai.runtime.cost;

import java.math.BigDecimal;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import io.quarkiverse.langchain4j.cost.CostEstimator;

@Priority(Integer.MIN_VALUE) // use min value so to ensure that user's beans are consulted first
@Singleton
public class BasicE3SmallCostEstimator implements CostEstimator {

    private static final BigDecimal COST = new BigDecimal("0.02").divide(BigDecimal.valueOf(1_000_000));

    @Override
    public boolean supports(SupportsContext context) {
        return "text-embedding-3-small".equals(context.model());
    }

    @Override
    public CostResult estimate(CostContext context) {
        Integer inputTokens = context.inputTokens();
        return new CostResult(COST.multiply(BigDecimal.valueOf(inputTokens)), BigDecimal.ZERO, "USD");
    }
}

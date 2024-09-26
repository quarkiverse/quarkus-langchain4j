package io.quarkiverse.langchain4j.openai.runtime.cost;

import java.math.BigDecimal;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import io.quarkiverse.langchain4j.cost.CostEstimator;

@Priority(Integer.MIN_VALUE) // use min value so to ensure that user's beans are consulted first
@Singleton
public class BasicGpt4oMiniCostEstimator implements CostEstimator {

    private static final BigDecimal INPUT_COST = new BigDecimal("0.15").divide(BigDecimal.valueOf(1_000_000));
    private static final BigDecimal OUTPUT_COST = new BigDecimal("0.6").divide(BigDecimal.valueOf(1_000_000));

    @Override
    public boolean supports(SupportsContext context) {
        return "gpt-4o-mini".equals(context.model());
    }

    @Override
    public CostResult estimate(CostContext context) {
        Integer inputTokens = context.inputTokens();
        Integer outputTokens = context.outputTokens();

        return new CostResult(INPUT_COST.multiply(BigDecimal.valueOf(inputTokens)),
                OUTPUT_COST.multiply(BigDecimal.valueOf(outputTokens)), "USD");
    }
}

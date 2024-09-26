package io.quarkiverse.langchain4j.cost;

import java.math.BigDecimal;

import io.smallrye.common.annotation.Experimental;

/**
 * Allows for user code to provide a custom strategy for estimating the cost of API calls
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface CostEstimator {

    /**
     * Determines whether the estimator applies to the request
     */
    boolean supports(SupportsContext context);

    CostResult estimate(CostContext context);

    interface SupportsContext {
        String model();
    }

    interface CostContext extends SupportsContext {
        Integer inputTokens();

        Integer outputTokens();
    }

    record CostResult(BigDecimal inputTokensCost, BigDecimal outputTokensCost, String currency) {

    }
}

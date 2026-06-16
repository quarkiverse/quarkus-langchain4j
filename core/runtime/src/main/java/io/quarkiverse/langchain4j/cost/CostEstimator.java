package io.quarkiverse.langchain4j.cost;

import java.math.BigDecimal;

import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
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

        ChatModelResponseContext responseContext();
    }

    interface CostContext extends SupportsContext {
        Integer inputTokens();

        Integer outputTokens();
    }

    record CostResult(BigDecimal inputTokensCost, BigDecimal outputTokensCost, BigDecimal cacheReadTokensCost,
            BigDecimal cacheCreationTokensCost, String currency) {

        /**
         * Convenience constructor for estimators that do not compute prompt cache costs.
         */
        public CostResult(BigDecimal inputTokensCost, BigDecimal outputTokensCost, String currency) {
            this(inputTokensCost, outputTokensCost, null, null, currency);
        }
    }
}

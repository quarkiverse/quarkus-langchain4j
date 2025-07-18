package io.quarkiverse.langchain4j.cost;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.inject.Inject;

import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.response.ResponseListener;
import io.quarkiverse.langchain4j.response.ResponseRecord;
import io.quarkus.arc.All;
import io.smallrye.common.annotation.Experimental;

/**
 * Allows for user code to provide a custom strategy for estimating the cost of API calls
 */
@Experimental("This feature is experimental and the API is subject to change")
public class CostEstimatorResponseListener implements ResponseListener {

    private final CostEstimatorService service;
    private final List<CostListener> listeners;

    @Inject
    public CostEstimatorResponseListener(CostEstimatorService service, @All List<CostListener> listeners) {
        this.service = service;
        this.listeners = new ArrayList<>(listeners);
        this.listeners.sort(Comparator.comparingInt(CostListener::order));
    }

    @Override
    public void onResponse(ResponseRecord rr) {
        String model = rr.model();
        TokenUsage tokenUsage = rr.tokenUsage();
        CostEstimator.CostContext context = new MyCostContext(tokenUsage, model);
        Cost cost = service.estimate(context);
        if (cost != null) {
            for (CostListener cl : listeners) {
                cl.handleCost(model, tokenUsage, cost);
            }
        }
    }

    private record MyCostContext(TokenUsage tokenUsage, String model) implements CostEstimator.CostContext {
        @Override
        public Integer inputTokens() {
            return tokenUsage().inputTokenCount();
        }

        @Override
        public Integer outputTokens() {
            return tokenUsage().outputTokenCount();
        }
    }
}

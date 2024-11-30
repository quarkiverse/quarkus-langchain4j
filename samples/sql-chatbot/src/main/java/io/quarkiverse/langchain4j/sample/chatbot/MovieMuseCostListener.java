package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.Cost;
import io.quarkiverse.langchain4j.cost.CostListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MovieMuseCostListener implements CostListener {
    public void handleCost(String model, TokenUsage tokenUsage, Cost cost) {
        System.out.println("model = " + model);
        System.out.println("tokenUsage = " + tokenUsage);
        System.out.println("cost = " + cost);
    }
}

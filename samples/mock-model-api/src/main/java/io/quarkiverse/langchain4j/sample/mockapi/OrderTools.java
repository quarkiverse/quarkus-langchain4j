package io.quarkiverse.langchain4j.sample.mockapi;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;

/**
 * A tool the assistant may call. The tests check that the mock's scripted tool call reaches this class and that its
 * result travels back to the model.
 */
@ApplicationScoped
public class OrderTools {

    private static final Map<String, String> STATUS = Map.of(
            "ORD-1001", "shipped on 2026-11-15, tracking number 1Z999",
            "ORD-1002", "waiting for payment");

    @Tool("Returns the status of an order")
    public String trackOrder(String orderId) {
        String status = STATUS.get(orderId);
        return status == null ? "unknown order " + orderId : orderId + ": " + status;
    }
}

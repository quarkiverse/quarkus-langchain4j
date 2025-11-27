package io.quarkiverse.langchain4j.sample.guardrails;

import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Input guardrail that implements rate limiting for tool invocations.
 */
@ApplicationScoped
public class RateLimitGuardrail implements ToolInputGuardrail {

    // Simple rate limiter: max 5 calls per minute per user/session
    private static final int MAX_CALLS_PER_MINUTE = 5;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    // In-memory storage (in production, use Redis or similar)
    private final Map<String, RateLimitEntry> rateLimits = new ConcurrentHashMap<>();

    @Override
    public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
        // Get user/session identifier (use memory ID as a proxy for user session)
        String userId = getUserIdentifier(request);
        String toolName = request.toolName();
        String key = userId + ":" + toolName;

        // Get or create rate limit entry
        RateLimitEntry entry = rateLimits.computeIfAbsent(key, k -> new RateLimitEntry());

        // Check rate limit
        long now = System.currentTimeMillis();
        synchronized (entry) {
            // Reset counter if window has expired
            if (now - entry.windowStart.get() > WINDOW_DURATION.toMillis()) {
                entry.windowStart.set(now);
                entry.count.set(0);
            }

            // Check if limit exceeded
            if (entry.count.get() >= MAX_CALLS_PER_MINUTE) {
                long timeUntilReset = entry.windowStart.get() + WINDOW_DURATION.toMillis() - now;
                return ToolInputGuardrailResult.failure(
                        "Rate limit exceeded for tool '" + toolName + "'. " +
                                "Maximum " + MAX_CALLS_PER_MINUTE + " calls per minute allowed. " +
                                "Please wait " + (timeUntilReset / 1000) + " seconds before trying again.");
            }

            // Increment counter
            entry.count.incrementAndGet();
        }

        // Rate limit check passed
        return ToolInputGuardrailResult.success();
    }

    private String getUserIdentifier(ToolInputGuardrailRequest request) {
        // Use memory ID as user identifier (in production, use actual user ID from security context)
        Object memoryId = request.memoryId();
        return memoryId != null ? memoryId.toString() : "anonymous";
    }

    /**
     * Simple rate limit entry holder.
     */
    private static class RateLimitEntry {
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        final AtomicInteger count = new AtomicInteger(0);
    }
}

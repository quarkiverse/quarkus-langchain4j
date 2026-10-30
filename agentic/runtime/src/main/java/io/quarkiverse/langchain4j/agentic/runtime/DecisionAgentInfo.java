package io.quarkiverse.langchain4j.agentic.runtime;

/**
 * The build-time facts about a decision agent that its configuration at startup needs.
 *
 * @param agentName the name of the agent, the key of its threshold configuration
 * @param booleanAnswer whether the agent has a {@code boolean} answer, the only kind a threshold applies to
 * @param hasThresholdSupplier whether the agent declares a {@code @DecisionThresholdSupplier} method, which takes
 *        precedence over the configuration
 */
public record DecisionAgentInfo(String agentName, boolean booleanAnswer, boolean hasThresholdSupplier) {
}

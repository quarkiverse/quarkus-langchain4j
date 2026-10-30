package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.agentic")
public interface AgenticRuntimeConfig {

    /**
     * The thresholds of the {@code boolean} answers of the decision agents, keyed by agent name: the {@code name} of
     * {@code @Agent}, or the name of the agent method. They apply to the agents that have a {@code boolean} answer
     * and no {@code @DecisionThresholdSupplier} method, and are read once at startup.
     */
    @WithName("decision-agent")
    @ConfigDocMapKey("agent-name")
    Map<String, DecisionAgentConfig> decisionAgents();

    interface DecisionAgentConfig {

        /**
         * The threshold of the {@code boolean} answers of this agent, between 0 and 1: an answer is {@code true} when
         * the probability of "yes" is at least the threshold. 0.5 when absent.
         */
        Optional<Double> threshold();

        /**
         * The threshold to use when the given decision model answered, keyed by model name, overriding
         * {@code threshold}. Thresholds are specific to a decision model and to its version.
         */
        @WithName("threshold-by-model")
        @ConfigDocMapKey("model-name")
        Map<String, Double> thresholdByModel();
    }
}

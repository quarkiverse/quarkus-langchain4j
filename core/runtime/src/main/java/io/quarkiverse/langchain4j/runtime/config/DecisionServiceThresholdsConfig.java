package io.quarkiverse.langchain4j.runtime.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.smallrye.config.WithName;

public interface DecisionServiceThresholdsConfig {

    /**
     * The threshold of the {@code boolean} answers of this Decision Service, between 0 and 1: an answer is
     * {@code true} when the probability of "yes" is at least the threshold. 0.5 when absent.
     */
    Optional<Double> threshold();

    /**
     * The threshold to use when the given decision model answered, keyed by model name, overriding {@code threshold}.
     * Thresholds are specific to a decision model and to its version.
     */
    @WithName("threshold-by-model")
    @ConfigDocMapKey("model-name")
    Map<String, Double> thresholdByModel();
}

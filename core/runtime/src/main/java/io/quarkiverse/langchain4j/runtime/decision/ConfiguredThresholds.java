package io.quarkiverse.langchain4j.runtime.decision;

import java.util.Map;
import java.util.Optional;

import dev.langchain4j.service.decision.ThresholdContext;
import dev.langchain4j.service.decision.ThresholdProvider;
import io.quarkus.runtime.configuration.ConfigurationException;

/**
 * The thresholds of the {@code boolean} answers of decision services and agents, read from the configuration.
 */
public final class ConfiguredThresholds {

    private ConfiguredThresholds() {
    }

    /**
     * The provider of the configured thresholds, or {@code null} when none is configured: the threshold of the model
     * that answered, else the default one, else {@code null}, which means 0.5.
     */
    public static ThresholdProvider provider(Optional<Double> threshold, Map<String, Double> thresholdByModel) {
        if (threshold.isEmpty() && thresholdByModel.isEmpty()) {
            return null;
        }
        return new ConfiguredThresholdProvider(threshold.orElse(null), Map.copyOf(thresholdByModel));
    }

    /**
     * Fails when a configured threshold is not between 0 and 1.
     *
     * @param prefix the configuration key of the thresholds, like
     *        {@code quarkus.langchain4j.decision-service."MyService"}
     */
    public static void validate(String prefix, Optional<Double> threshold, Map<String, Double> thresholdByModel) {
        if (threshold.isPresent()) {
            validate(prefix + ".threshold", threshold.get());
        }
        for (Map.Entry<String, Double> entry : thresholdByModel.entrySet()) {
            validate(prefix + ".threshold-by-model.\"" + entry.getKey() + "\"", entry.getValue());
        }
    }

    private static void validate(String key, double threshold) {
        if (!(threshold >= 0 && threshold <= 1)) {
            throw new ConfigurationException(
                    "The value of '%s' must be between 0 and 1, but was %s".formatted(key, threshold));
        }
    }

    private static final class ConfiguredThresholdProvider implements ThresholdProvider {

        private final Double defaultThreshold;
        private final Map<String, Double> modelThresholds;

        private ConfiguredThresholdProvider(Double defaultThreshold, Map<String, Double> modelThresholds) {
            this.defaultThreshold = defaultThreshold;
            this.modelThresholds = modelThresholds;
        }

        @Override
        public Double threshold(ThresholdContext context) {
            Double modelThreshold = context.modelName() == null ? null : modelThresholds.get(context.modelName());
            return modelThreshold != null ? modelThreshold : defaultThreshold;
        }
    }
}

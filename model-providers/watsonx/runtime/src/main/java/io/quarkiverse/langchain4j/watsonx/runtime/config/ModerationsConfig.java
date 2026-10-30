package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;
import java.util.OptionalDouble;

import io.quarkus.runtime.annotations.ConfigGroup;

/**
 * Inline moderation applied by watsonx.ai while the chat request is served.
 * <p>
 * Every detector is opt-in: a detector is only sent to watsonx.ai when at least one of its <code>input</code> or
 * <code>output</code> properties is set.
 */
@ConfigGroup
public interface ModerationsConfig {

    /**
     * Hate, abuse and profanity (HAP) detection.
     */
    Optional<HapConfig> hap();

    /**
     * Personally identifiable information (PII) detection.
     */
    Optional<PiiConfig> pii();

    /**
     * Granite Guardian detection.
     */
    Optional<GraniteGuardianConfig> graniteGuardian();

    @ConfigGroup
    interface HapConfig {

        /**
         * Enables HAP detection on the input text, using the given threshold score.
         */
        OptionalDouble input();

        /**
         * Enables HAP detection on the output text, using the given threshold score.
         */
        OptionalDouble output();

        /**
         * Whether the detected entity value is removed from the text instead of being returned.
         */
        Optional<Boolean> mask();
    }

    @ConfigGroup
    interface PiiConfig {

        /**
         * Whether PII detection is applied to the input text.
         */
        Optional<Boolean> input();

        /**
         * Whether PII detection is applied to the output text.
         */
        Optional<Boolean> output();

        /**
         * Whether the detected entity value is removed from the text instead of being returned.
         */
        Optional<Boolean> mask();
    }

    @ConfigGroup
    interface GraniteGuardianConfig {

        /**
         * Enables Granite Guardian detection on the input text, using the given threshold score.
         * <p>
         * Granite Guardian is only applied to the input text.
         */
        OptionalDouble input();

        /**
         * Whether the detected entity value is removed from the text instead of being returned.
         */
        Optional<Boolean> mask();
    }
}

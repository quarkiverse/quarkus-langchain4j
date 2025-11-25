package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ModerationModelConfig {

    /**
     * Configuration for Personally Identifiable Information (PII) moderation model.
     */
    Optional<PiiConfig> pii();

    /**
     * Configuration for the HAP moderation model.
     */
    Optional<HapConfig> hap();

    /**
     * Configuration for the Granite Guardian moderation model.
     */
    Optional<GraniteGuardianConfig> graniteGuardian();

    /**
     * Whether moderation model requests should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether moderation model responses should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Whether the watsonx.ai client should log requests as cURL commands.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequestsCurl();

    @ConfigGroup
    public interface PiiConfig {

        /**
         * Indicates whether the PII moderation model is enabled.
         */
        Boolean enabled();
    }

    @ConfigGroup
    public interface HapConfig {

        /**
         * Indicates whether the HAP moderation model is enabled.
         */
        Boolean enabled();

        /**
         * Threshold value for HAP moderation model.
         */
        Optional<Double> threshold();
    }

    @ConfigGroup
    public interface GraniteGuardianConfig {

        /**
         * Indicates whether the GraniteGuardian moderation model is enabled.
         */
        Boolean enabled();

        /**
         * Threshold value for Granite Guardian moderation model.
         */
        Optional<Double> threshold();
    }
}

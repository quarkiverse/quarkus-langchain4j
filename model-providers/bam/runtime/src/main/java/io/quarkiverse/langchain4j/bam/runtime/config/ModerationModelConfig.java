package io.quarkiverse.langchain4j.bam.runtime.config;

import java.util.List;
import java.util.Optional;

import dev.langchain4j.data.message.ChatMessageType;
import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ModerationModelConfig {

    /**
     * What types of messages are subject to moderation checks.
     */
    @WithDefault("user")
    List<ChatMessageType> messagesToModerate();

    /**
     * The HAP detector is intended to identify hateful, abusive, and/or profane language.
     * <p>
     * The float is a value from 0.1 to 1 that allows you to control when a content must be flagged by the detector.
     */
    Optional<Float> hap();

    /**
     * The social bias detector is intended to identify subtle forms of hate speech and discriminatory content which may easily
     * go
     * undetected by keyword detection systems or HAP classifiers.
     * <p>
     * The float is a value from 0.1 to 1 that allows you to control when a content must be flagged by the detector.
     */
    Optional<Float> socialBias();

    /**
     * Whether the BAM moderation model should log requests
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether the BAM moderation model should log requests
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();
}

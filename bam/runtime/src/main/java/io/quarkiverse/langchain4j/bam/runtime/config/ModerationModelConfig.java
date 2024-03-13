package io.quarkiverse.langchain4j.bam.runtime.config;

import java.util.List;
import java.util.Optional;

import dev.langchain4j.data.message.ChatMessageType;
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
     * The implicit hate detector is intended to identify subtle forms of hate speech. The float is a value from 0.1 to 1 that
     * allows
     * you to control when a content must be flagged by the detector.
     */
    Optional<Float> implicitHate();

    /**
     * The HAP detector is intended to identify hateful, abusive, and/or profane language. The float is a value from 0.1 to 1
     * that
     * allows you to control when a content must be flagged by the detector.
     */
    Optional<Float> hap();

    /**
     * The stigma detector is intended to identify discrimination that is based on social attributes or characteristics. The
     * float is
     * a value from 0.1 to 1 that allows you to control when a content must be flagged by the detector.
     */
    Optional<Float> stigma();
}

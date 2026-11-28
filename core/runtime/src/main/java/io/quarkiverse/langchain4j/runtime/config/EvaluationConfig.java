package io.quarkiverse.langchain4j.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/**
 * Properties the evaluation framework sets while tests run. They are declared so that Quarkus does not warn about
 * them as unrecognized; nothing in the extension reads them.
 */
@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.evaluation")
@SuppressWarnings("unused")
public interface EvaluationConfig {

    /**
     * Identifies the current test run, set by the {@code evaluation-junit5} module each time the JUnit Platform starts
     * executing tests. Internal, not meant to be configured.
     */
    Optional<String> run();
}

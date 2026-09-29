package io.quarkiverse.langchain4j.sample.codereviewer;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "gh-pr-code-reviewer")
public interface CodeReviewerConfig {

    /**
     * How much of the diff the model is given, in characters. The prompt and the review have to fit
     * in the model's context together (with JitLLM, {@code
     * quarkus.langchain4j.jitllm.chat-model.max-tokens}).
     */
    @WithDefault("60000")
    int maxDiffChars();

    /**
     * How much of the pull request description the model is given, in characters. It is context,
     * not the subject of the review.
     */
    @WithDefault("4000")
    int maxDescriptionChars();

    /**
     * How often to report progress while the model loads and reads the prompt, before its first
     * token.
     */
    @WithDefault("15s")
    Duration progressInterval();
}

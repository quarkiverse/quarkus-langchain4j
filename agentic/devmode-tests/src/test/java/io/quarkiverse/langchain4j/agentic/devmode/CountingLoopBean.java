package io.quarkiverse.langchain4j.agentic.devmode;

import jakarta.inject.Inject;

import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.service.V;

/**
 * A class-based agent: a bean whose agentic method is native and whose exit condition is an instance method, modified
 * by {@link ClassBasedAgentDevModeTest}.
 */
public class CountingLoopBean {

    @Inject
    LoopThreshold threshold;

    @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = CountingLoopAgent.Counter.class)
    public native int count(@V("count") int count);

    @ExitCondition
    boolean reached(@V("count") int count) {
        return count >= threshold.value();
    }
}

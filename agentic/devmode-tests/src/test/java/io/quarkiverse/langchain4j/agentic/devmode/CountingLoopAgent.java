package io.quarkiverse.langchain4j.agentic.devmode;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;

public interface CountingLoopAgent {

    @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = Counter.class)
    int count(@V("count") int count);

    @ExitCondition
    static boolean reached(@V("count") int count, @CdiBean LoopThreshold threshold) {
        return count >= threshold.value();
    }

    class Counter {

        @Agent(outputKey = "count")
        public static int increment(@V("count") int count) {
            return count + 1;
        }
    }
}

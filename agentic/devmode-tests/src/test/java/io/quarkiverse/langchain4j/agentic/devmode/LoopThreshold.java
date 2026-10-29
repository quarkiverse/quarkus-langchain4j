package io.quarkiverse.langchain4j.agentic.devmode;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Bean used only as a {@code @CdiBean} parameter of an exit condition, modified by {@link CdiHelperParameterDevModeTest}.
 */
@ApplicationScoped
public class LoopThreshold {

    public int value() {
        return 3;
    }
}

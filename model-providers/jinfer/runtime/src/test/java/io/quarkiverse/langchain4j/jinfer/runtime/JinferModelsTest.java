package io.quarkiverse.langchain4j.jinfer.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The close registry must be JVM-wide (static) so that models registered during the bean-generating
 * recorder's build step are visible to the (separate) shutdown build step's recorder instance.
 */
public class JinferModelsTest {

    @Test
    void registeredCloseablesAreClosedExactlyOnceThenCleared() {
        AtomicInteger closes = new AtomicInteger();
        JinferModels.registerForClose(() -> closes.incrementAndGet());

        JinferModels.closeAll();
        assertThat(closes.get()).isEqualTo(1);

        // Registry is cleared after closeAll, so a second shutdown closes nothing again.
        JinferModels.closeAll();
        assertThat(closes.get()).isEqualTo(1);
    }
}

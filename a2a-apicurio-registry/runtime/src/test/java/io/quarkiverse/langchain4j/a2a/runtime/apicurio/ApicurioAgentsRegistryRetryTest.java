package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ApicurioAgentsRegistryRetryTest {

    /** Reproduces the exact exception shape the JDK adapter throws on a dropped connection. */
    private static RuntimeException droppedConnection() {
        return new RuntimeException("could not close the reader",
                new IOException("fixed content-length: 499, bytes received: 0"));
    }

    @Test
    void retriesAndSucceedsAfterATransientConnectionDrop() {
        AtomicInteger calls = new AtomicInteger();

        String result = ApicurioAgentsRegistry.withRetry(() -> {
            if (calls.incrementAndGet() < 3) {
                throw droppedConnection();
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, calls.get());
    }

    @Test
    void propagatesWhenEveryAttemptHitsAConnectionDrop() {
        AtomicInteger calls = new AtomicInteger();

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> ApicurioAgentsRegistry.<String> withRetry(() -> {
                    calls.incrementAndGet();
                    throw droppedConnection();
                }));

        assertEquals("could not close the reader", thrown.getMessage());
        // Bounded: the helper gives up after MAX_ATTEMPTS rather than retrying forever.
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotRetryFailuresUnrelatedToIo() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalStateException.class,
                () -> ApicurioAgentsRegistry.<String> withRetry(() -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("bad request");
                }));

        // A non-IO failure is not transient: it propagates on the first attempt.
        assertEquals(1, calls.get());
    }
}

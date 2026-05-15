package io.quarkiverse.langchain4j.test.aiservice;

import io.quarkiverse.langchain4j.runtime.PreventsErrorHandlerExecution;

/**
 * Bespoke RuntimeException used by {@code ParallelToolDispatchNonStreamingTest$CancellationTest} to verify that
 * exceptions raised inside a parallel-executor task surface unchanged (no wrapping in
 * {@code java.util.concurrent.ExecutionException}) when the gather thread re-throws.
 * <p>
 * Implements {@link PreventsErrorHandlerExecution} so the langchain4j {@code ToolService} default
 * {@code ToolExecutionErrorHandler} does not absorb the exception into a {@code ToolExecutionResult}
 * (which would let the loop continue) — this guarantees the exception bubbles all the way to the AiService
 * caller, which is the parallel-mode contract we want to assert.
 * <p>
 * Lives at the top level (rather than as a static nested class) so the QuarkusUnitTest deployment archive can
 * load this class without also having to resolve the enclosing test class — the outer test class is
 * intentionally excluded from the archive to avoid CDI / extension-init pulling it in.
 */
public class BespokeParallelDispatchException extends RuntimeException implements PreventsErrorHandlerExecution {

    public BespokeParallelDispatchException(String message) {
        super(message);
    }
}

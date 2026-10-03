package io.quarkiverse.langchain4j.a2a.server.runtime.executor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.spec.Part;
import org.junit.jupiter.api.Test;

/**
 * What a result with nothing in it turns into. Every other result travels a path the end-to-end tests already
 * cover, and only this one has no request that produces it.
 */
public class QuarkusBaseAgentExecutorTest {

    private static final QuarkusBaseAgentExecutor EXECUTOR = new QuarkusBaseAgentExecutor() {

        @Override
        protected List<Part<?>> invoke(RequestContext context) {
            throw new UnsupportedOperationException("The tests here do not run a task");
        }
    };

    /**
     * An AI Service that returned nothing has no result to carry, and an artifact with no parts states that more
     * plainly than one holding an empty part would.
     */
    @Test
    public void testANullResultBecomesNoPartsAtAll() {
        assertTrue(EXECUTOR.objectResultToParts(null).isEmpty());
    }
}

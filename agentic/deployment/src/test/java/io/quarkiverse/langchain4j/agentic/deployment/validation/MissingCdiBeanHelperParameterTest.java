package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that a {@code @CdiBean} parameter without a matching bean fails the build, instead of failing when the
 * agent invokes the method declaring it.
 */
public class MissingCdiBeanHelperParameterTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("No CDI bean of type '" + NotABean.class.getName() + "'")
                    .hasStackTraceContaining("parameter no.1 of method 'reached'"));

    @Test
    public void test() {
        fail("should never be called");
    }

    /** Not a bean: it has no bean defining annotation. */
    public static class NotABean {
        public int threshold() {
            return 3;
        }
    }

    public static class Counter {
        @Agent(outputKey = "count")
        public static int increment(@V("count") int count) {
            return count + 1;
        }
    }

    public interface CountingLoop {
        @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = Counter.class)
        int count(@V("count") int count);

        @ExitCondition
        static boolean reached(@V("count") int count, @CdiBean NotABean bean) {
            return count >= bean.threshold();
        }
    }
}

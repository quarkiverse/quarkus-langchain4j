package io.quarkiverse.langchain4j.test.decision;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionServiceDecideOnObjectAnswerShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("annotate its fields with @Decide instead of the method"));

    public record Verdict(@Decide("Is this message spam?") boolean spam) {
    }

    @RegisterDecisionService
    public interface SpamFilter {
        @Decide("What about this message?")
        Verdict check(String message);
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}

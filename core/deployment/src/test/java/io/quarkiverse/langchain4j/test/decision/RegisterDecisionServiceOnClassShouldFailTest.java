package io.quarkiverse.langchain4j.test.decision;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.test.QuarkusUnitTest;

public class RegisterDecisionServiceOnClassShouldFailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("@RegisterDecisionService can only be used on interfaces"));

    @RegisterDecisionService
    public static abstract class SpamFilter {
        @Decide("Is this message spam?")
        public abstract boolean isSpam(String message);
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}

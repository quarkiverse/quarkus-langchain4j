package io.quarkiverse.langchain4j.test.decision;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.test.QuarkusUnitTest;

public class DecisionServiceWithoutMethodShouldFailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("has no abstract method to implement"));

    @RegisterDecisionService
    public interface SpamFilter {
        default boolean isSpam(String message) {
            return false;
        }
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}

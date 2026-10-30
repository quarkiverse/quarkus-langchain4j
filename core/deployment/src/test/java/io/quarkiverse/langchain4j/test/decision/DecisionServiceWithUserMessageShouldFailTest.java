package io.quarkiverse.langchain4j.test.decision;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.test.QuarkusUnitTest;

public class DecisionServiceWithUserMessageShouldFailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("must not use @UserMessage"));

    @RegisterDecisionService
    public interface SpamFilter {
        @UserMessage("Is {{message}} spam?")
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}

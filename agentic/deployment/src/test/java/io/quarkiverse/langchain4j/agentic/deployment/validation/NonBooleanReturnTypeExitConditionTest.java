package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkus.test.QuarkusUnitTest;

public class NonBooleanReturnTypeExitConditionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(Agents.class, StyleReviewLoopAgentWithCounter.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("boolean"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface StyleReviewLoopAgentWithCounter {

        @LoopAgent(description = "Review the given story to ensure it aligns with the specified style", outputName = "story", maxIterations = 5, subAgents = {
                @SubAgent(type = Agents.StyleScorer.class, outputName = "score"),
                @SubAgent(type = Agents.StyleEditor.class, outputName = "story")
        })
        String write(@V("story") String story);

        @ExitCondition(testExitAtLoopEnd = true)
        static Object exit(@V("score") double score, @LoopCounter int loopCounter) {
            return score >= 0.8;
        }
    }
}

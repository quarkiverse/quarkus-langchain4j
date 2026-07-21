package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.BeforeCall;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkus.test.QuarkusUnitTest;

public class NonStaticBeforeCallTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(Agents.class, StoryCreatorWithBeforeCall.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("static"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface StoryCreatorWithBeforeCall {

        @SequenceAgent(outputKey = "story", subAgents = { Agents.CreativeWriter.class, Agents.StyleEditor.class })
        String write(@V("topic") String topic, @V("style") String style);

        @BeforeCall
        default void beforeCall(AgenticScope agenticScope) {
            agenticScope.writeStateIfAbsent("style", "comedy");
        }
    }
}

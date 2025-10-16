package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkus.test.QuarkusUnitTest;

public class NonErrorContextParameterTypeErrorHandlerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(Agents.class, StoryCreatorWithErrorRecovery.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("ErrorContext"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface StoryCreatorWithErrorRecovery {

        @SequenceAgent(outputName = "story", subAgents = {
                @SubAgent(type = Agents.CreativeWriter.class, outputName = "story"),
                @SubAgent(type = Agents.AudienceEditor.class, outputName = "story"),
                @SubAgent(type = Agents.StyleEditor.class, outputName = "story")
        })
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);

        @ErrorHandler
        static ErrorRecoveryResult errorHandler() {
            return ErrorRecoveryResult.throwException();
        }
    }
}

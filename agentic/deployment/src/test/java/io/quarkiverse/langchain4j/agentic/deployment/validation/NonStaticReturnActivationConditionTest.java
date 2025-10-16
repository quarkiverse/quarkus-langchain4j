package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkus.test.QuarkusUnitTest;

public class NonStaticReturnActivationConditionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Agents.class, ExpertsAgent.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("static"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface ExpertsAgent {

        @ConditionalAgent(outputName = "response", subAgents = {
                @SubAgent(type = Agents.MedicalExpert.class, outputName = "response"),
                @SubAgent(type = Agents.TechnicalExpert.class, outputName = "response"),
                @SubAgent(type = Agents.LegalExpert.class, outputName = "response")
        })
        String askExpert(@V("request") String request);

        @ActivationCondition(Agents.MedicalExpert.class)
        static boolean activateMedical(Agents.RequestCategory category) {
            return category == Agents.RequestCategory.MEDICAL;
        }

        @ActivationCondition(Agents.LegalExpert.class)
        default boolean activateLegal(Agents.RequestCategory category) {
            return category == Agents.RequestCategory.MEDICAL;
        }
    }
}

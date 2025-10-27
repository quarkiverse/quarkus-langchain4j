package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkus.test.QuarkusUnitTest;

public class NonBooleanReturnTypeActivationConditionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Agents.class, ExpertsAgent.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("boolean"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface ExpertsAgent {

        @ConditionalAgent(outputKey = "response", subAgents = {
                @SubAgent(type = Agents.MedicalExpert.class, outputKey = "response"),
                @SubAgent(type = Agents.TechnicalExpert.class, outputKey = "response"),
                @SubAgent(type = Agents.LegalExpert.class, outputKey = "response")
        })
        String askExpert(@V("request") String request);

        @ActivationCondition(Agents.MedicalExpert.class)
        static boolean activateMedical(@V("category") Agents.RequestCategory category) {
            return category == Agents.RequestCategory.MEDICAL;
        }

        @ActivationCondition(Agents.LegalExpert.class)
        static int activateLegal(AgenticScope agenticScope) {
            return 1;
        }
    }
}

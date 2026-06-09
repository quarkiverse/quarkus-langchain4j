package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.quarkus.test.QuarkusUnitTest;

public class AgentGuardrailMissingBeanTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(AgentWithMissingGuardrail.class, NotACdiBean.class))
            .assertException(t -> {
                assertThat(t).isInstanceOf(DeploymentException.class);
                assertThat(t).hasMessageContaining(
                        "io.quarkiverse.langchain4j.agentic.deployment.AgentGuardrailMissingBeanTest$NotACdiBean");
            });

    @Test
    @ActivateRequestContext
    void buildShouldFailWithMissingGuardrailBean() {
        fail("Should not be called — build should fail");
    }

    public interface AgentWithMissingGuardrail {

        @Agent
        @InputGuardrails(NotACdiBean.class)
        @UserMessage("Process this")
        String process();
    }

    public static class NotACdiBean implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
            return success();
        }
    }
}

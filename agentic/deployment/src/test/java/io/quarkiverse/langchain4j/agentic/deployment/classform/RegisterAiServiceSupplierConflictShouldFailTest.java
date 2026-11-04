package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;

public class RegisterAiServiceSupplierConflictShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining(
                            "attribute of @RegisterAiService and declares a method annotated with @ToolsSupplier"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public static class Calculator {
        @dev.langchain4j.agent.tool.Tool
        public int add(int a, int b) {
            return a + b;
        }
    }

    @RegisterAiService(tools = Calculator.class)
    public static class MathAgent {
        @UserMessage("Compute {{expression}}")
        @Agent(outputKey = "result")
        public native String compute(@V("expression") String expression);

        @ToolsSupplier
        Object tools() {
            return new Calculator();
        }
    }
}

package io.quarkiverse.langchain4j.test.guardrails;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Test that should FAIL at build time because there are multiple CDI beans that match a guardrail class.
 */
public class MultipleCDIBeansMatchGuardrailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, Alpha.class, Bravo.class))
            .assertException(t -> {
                // Expect build-time validation error for non-CDI bean guardrail
                if (t.getMessage() != null
                        && t.getMessage().contains("There must be exactly one bean that matches the input guardrail")) {
                    System.out.println("✓ Correctly caught multiple CDI beans matching a guardrail: " + t.getMessage());
                } else {
                    throw new AssertionError(
                            "Expected ' multiple CDI beans matching a guardrail' error but got: " + t.getMessage());
                }
            });

    @Test
    void testShouldFailAtBuildTime() {
        // This test should never run - build should fail
    }

    public static class MyTools {

        @Tool("Test tool")
        @ToolInputGuardrails(Alpha.class)
        public String testTool(String input) {
            return "result";
        }
    }

    @ApplicationScoped
    public static class Alpha implements ToolInputGuardrail {

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return ToolInputGuardrailResult.success();
        }
    }

    @Unremovable // Alpha is marked as unremovable because it's used on a tool
    @ApplicationScoped
    public static class Bravo extends Alpha {

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return ToolInputGuardrailResult.success();
        }
    }
}

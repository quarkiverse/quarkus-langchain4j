package io.quarkiverse.langchain4j.test.guardrails;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Test that should FAIL at build time because the guardrail is NOT a CDI bean.
 */
public class NonCDIBeanGuardrailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyTools.class, NotACDIBeanGuardrail.class))
            .assertException(t -> {
                // Expect build-time validation error for non-CDI bean guardrail
                if (t.getMessage() != null && t.getMessage().contains("is not a CDI bean")) {
                    System.out.println("✓ Correctly caught non-CDI bean guardrail: " + t.getMessage());
                } else {
                    throw new AssertionError("Expected 'is not a CDI bean' error but got: " + t.getMessage());
                }
            });

    @Test
    void testShouldFailAtBuildTime() {
        // This test should never run - build should fail
    }

    public static class MyTools {
        @Tool("Test tool")
        @ToolInputGuardrails({ NotACDIBeanGuardrail.class })
        public String testTool(String input) {
            return "result";
        }
    }

    // NOTE: This class is intentionally NOT annotated with @ApplicationScoped or any CDI annotation
    public static class NotACDIBeanGuardrail implements ToolInputGuardrail {

        // introduce a no-args constructor
        public NotACDIBeanGuardrail(String dummy) {
        }

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return ToolInputGuardrailResult.success();
        }
    }
}

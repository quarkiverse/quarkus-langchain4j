package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Integration tests for tool input guardrails.
 * Tests that guardrails are properly registered as CDI beans and can be invoked.
 * Full end-to-end execution with AI Services is tested in separate integration tests.
 */
class ToolInputGuardrailsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyTools.class,
                            ValidationGuardrail.class,
                            TransformGuardrail.class,
                            FailureGuardrail.class,
                            FatalFailureGuardrail.class,
                            FirstGuardrail.class,
                            SecondGuardrail.class));

    @Inject
    TransformGuardrail transformGuardrail;

    @Inject
    FailureGuardrail failureGuardrail;

    @Inject
    FatalFailureGuardrail fatalFailureGuardrail;

    @Inject
    FirstGuardrail firstGuardrail;

    @Inject
    SecondGuardrail secondGuardrail;

    @BeforeEach
    void setUp() {
        // Reset all guardrail execution counters
        ValidationGuardrail.reset();
        TransformGuardrail.reset();
        FailureGuardrail.reset();
        FatalFailureGuardrail.reset();
        FirstGuardrail.reset();
        SecondGuardrail.reset();
    }

    @Test
    void testGuardrails_registeredInMetadata() {
        // Verify that guardrails are properly registered in tool metadata
        List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata().get(MyTools.class.getName());
        assertThat(methodCreateInfos).isNotNull().hasSize(5);

        // Find validatedTool and verify it has input guardrails
        ToolMethodCreateInfo validatedToolInfo = methodCreateInfos.stream()
                .filter(info -> "validatedTool".equals(info.toolSpecification().name()))
                .findFirst()
                .orElse(null);
        assertThat(validatedToolInfo).isNotNull();
        assertThat(validatedToolInfo.getInputGuardrails()).isNotNull();
        assertThat(validatedToolInfo.getInputGuardrails().hasGuardrails()).isTrue();
        assertThat(validatedToolInfo.getInputGuardrails().getClassNames())
                .contains("io.quarkiverse.langchain4j.test.guardrails.ToolInputGuardrailsTest$ValidationGuardrail");

        // Find chainedTool and verify it has multiple guardrails
        ToolMethodCreateInfo chainedToolInfo = methodCreateInfos.stream()
                .filter(info -> "chainedTool".equals(info.toolSpecification().name()))
                .findFirst()
                .orElse(null);
        assertThat(chainedToolInfo).isNotNull();
        assertThat(chainedToolInfo.getInputGuardrails()).isNotNull();
        assertThat(chainedToolInfo.getInputGuardrails().getClassNames()).hasSize(2);
    }

    @Test
    void testInputGuardrail_success() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("validatedTool")
                .arguments("{\"input\": \"hello\"}")
                .build();

        ToolInputGuardrailRequest guardrailRequest = new ToolInputGuardrailRequest(request, null, null);
        ToolInputGuardrailResult result = new ValidationGuardrail().validate(guardrailRequest);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.modifiedRequest()).isNull();
        assertThat(result.errorMessage()).isNull();
        assertThat(ValidationGuardrail.executionCount).isEqualTo(1);
        assertThat(ValidationGuardrail.lastRequest).isNotNull();
        assertThat(ValidationGuardrail.lastRequest.toolName()).isEqualTo("validatedTool");
    }

    @Test
    void testInputGuardrail_modifiesRequest() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("transformedTool")
                .arguments("{\"input\": \"hello\"}")
                .build();

        ToolInputGuardrailRequest guardrailRequest = new ToolInputGuardrailRequest(request, null, null);
        ToolInputGuardrailResult result = transformGuardrail.validate(guardrailRequest);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.modifiedRequest()).isNotNull();
        assertThat(result.modifiedRequest().arguments()).contains("HELLO");
        assertThat(TransformGuardrail.executionCount).isEqualTo(1);
    }

    @Test
    void testInputGuardrail_failure() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("failingTool")
                .arguments("{\"input\": \"invalid\"}")
                .build();

        ToolInputGuardrailRequest guardrailRequest = new ToolInputGuardrailRequest(request, null, null);
        ToolInputGuardrailResult result = failureGuardrail.validate(guardrailRequest);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorMessage()).contains("Input validation failed");
        assertThat(FailureGuardrail.executionCount).isEqualTo(1);
    }

    @Test
    void testInputGuardrail_fatalFailure() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("fatalTool")
                .arguments("{\"input\": \"anything\"}")
                .build();

        ToolInputGuardrailRequest guardrailRequest = new ToolInputGuardrailRequest(request, null, null);
        ToolInputGuardrailResult result = fatalFailureGuardrail.validate(guardrailRequest);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorMessage()).contains("Fatal validation error");
        assertThat(result.cause()).isInstanceOf(SecurityException.class);
        assertThat(FatalFailureGuardrail.executionCount).isEqualTo(1);
    }

    @Test
    void testInputGuardrails_canBeChained() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("chainedTool")
                .arguments("{\"input\": \"test\"}")
                .build();

        // Simulate chaining - execute first guardrail
        ToolInputGuardrailRequest firstRequest = new ToolInputGuardrailRequest(request, null, null);
        ToolInputGuardrailResult firstResult = firstGuardrail.validate(firstRequest);

        assertThat(firstResult.isSuccess()).isTrue();
        assertThat(FirstGuardrail.executionCount).isEqualTo(1);

        // Execute second guardrail
        ToolInputGuardrailRequest secondRequest = new ToolInputGuardrailRequest(request, null, null);
        ToolInputGuardrailResult secondResult = secondGuardrail.validate(secondRequest);

        assertThat(secondResult.isSuccess()).isTrue();
        assertThat(SecondGuardrail.executionCount).isEqualTo(1);

        // Verify execution order
        assertThat(SecondGuardrail.executionOrder).isGreaterThan(FirstGuardrail.executionOrder);
    }

    @Test
    void testInputGuardrail_chainStopsOnFailure() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("chainedTool")
                .arguments("{\"input\": \"fail-first\"}")
                .build();

        // Simulate chaining - execute first guardrail (should fail)
        ToolInputGuardrailRequest firstRequest = new ToolInputGuardrailRequest(request, null, null);
        ToolInputGuardrailResult firstResult = firstGuardrail.validate(firstRequest);

        assertThat(firstResult.isSuccess()).isFalse();
        assertThat(firstResult.errorMessage()).contains("First guardrail failed");
        assertThat(FirstGuardrail.executionCount).isEqualTo(1);

        // Second guardrail should NOT be executed in real scenario
        assertThat(SecondGuardrail.executionCount).isEqualTo(0);
    }

    // Tools
    private static class MyTools {
        @Tool
        @ToolInputGuardrails({ ValidationGuardrail.class })
        String validatedTool(String input) {
            return "Validated: " + input;
        }

        @Tool
        @ToolInputGuardrails({ TransformGuardrail.class })
        String transformedTool(String input) {
            return "Transformed: " + input;
        }

        @Tool
        @ToolInputGuardrails({ FailureGuardrail.class })
        String failingTool(String input) {
            return "Should not execute";
        }

        @Tool
        @ToolInputGuardrails({ FatalFailureGuardrail.class })
        String fatalTool(String input) {
            return "Should not execute";
        }

        @Tool
        @ToolInputGuardrails({ FirstGuardrail.class, SecondGuardrail.class })
        String chainedTool(String input) {
            return "Chained: " + input;
        }
    }

    // Guardrails

    public static class ValidationGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;
        static ToolInputGuardrailRequest lastRequest = null;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            lastRequest = request;
            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
            lastRequest = null;
        }
    }

    @ApplicationScoped
    public static class TransformGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;

            // Transform input to uppercase in JSON
            String args = request.arguments();
            String transformedArgs = args.replace("hello", "HELLO");

            ToolExecutionRequest modifiedRequest = ToolExecutionRequest.builder()
                    .id(request.executionRequest().id())
                    .name(request.executionRequest().name())
                    .arguments(transformedArgs)
                    .build();

            return ToolInputGuardrailResult.successWith(modifiedRequest);
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class FailureGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;

            if (request.arguments().contains("invalid")) {
                return ToolInputGuardrailResult.failure("Input validation failed: contains 'invalid'");
            }

            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class FatalFailureGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            return ToolInputGuardrailResult.fatal(
                    "Fatal validation error",
                    new SecurityException("Unauthorized access"));
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class FirstGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;
        static long executionOrder = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            executionOrder = System.nanoTime();

            if (request.arguments().contains("fail-first")) {
                return ToolInputGuardrailResult.failure("First guardrail failed");
            }

            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
            executionOrder = 0;
        }
    }

    @ApplicationScoped
    public static class SecondGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;
        static long executionOrder = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            executionOrder = System.nanoTime();
            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
            executionOrder = 0;
        }
    }
}

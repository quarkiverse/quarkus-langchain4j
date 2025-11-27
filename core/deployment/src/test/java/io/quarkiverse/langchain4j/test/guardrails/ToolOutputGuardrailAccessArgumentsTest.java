package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

/**
 * Tests that output guardrails can access the original function parameters
 * both as raw JSON string and as parsed JsonObject.
 */
public class ToolOutputGuardrailAccessArgumentsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            ArgumentAccessingGuardrail.class,
                            InputOutputValidatingGuardrail.class,
                            Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @BeforeEach
    void setUp() {
        ArgumentAccessingGuardrail.reset();
        InputOutputValidatingGuardrail.reset();
        tools.reset();
    }

    @Test
    @ActivateRequestContext
    void outputGuardrail_canAccessArgumentsAsRawString() {
        String ignored = aiService.chat("test", "processData - 100 - 50");

        // Verify guardrail was executed
        assertThat(ArgumentAccessingGuardrail.executed).isTrue();

        // Verify guardrail could access raw arguments string
        assertThat(ArgumentAccessingGuardrail.rawArguments)
                .isNotNull()
                .contains("\"maxValue\":100")
                .contains("\"minValue\":50");

        // Verify tool executed
        assertThat(tools.processDataExecuted).isTrue();
    }

    @Test
    @ActivateRequestContext
    void outputGuardrail_canAccessArgumentsAsJsonObject() {
        String ignored = aiService.chat("test", "processData - 200 - 25");

        // Verify guardrail was executed
        assertThat(ArgumentAccessingGuardrail.executed).isTrue();

        // Verify guardrail could parse and access arguments as JsonObject
        assertThat(ArgumentAccessingGuardrail.parsedArguments).isNotNull();
        assertThat(ArgumentAccessingGuardrail.maxValueFromArgs).isEqualTo(200);
        assertThat(ArgumentAccessingGuardrail.minValueFromArgs).isEqualTo(25);

        // Verify tool executed
        assertThat(tools.processDataExecuted).isTrue();
    }

    @Test
    @ActivateRequestContext
    void outputGuardrail_canValidateOutputAgainstInputParameters() {
        // This should succeed - output value 75 is between min=50 and max=100
        String result = aiService.chat("test", "calculateValue - 100 - 50");

        assertThat(InputOutputValidatingGuardrail.executed).isTrue();
        assertThat(InputOutputValidatingGuardrail.validationPassed).isTrue();
        assertThat(result).contains("Value: 75");
    }

    @Test
    @ActivateRequestContext
    void outputGuardrail_failsWhenOutputViolatesInputConstraints() {
        // This should fail - output value 150 exceeds max=100
        String result = aiService.chat("test", "calculateValue - 100 - 10");

        assertThat(InputOutputValidatingGuardrail.executed).isTrue();
        assertThat(InputOutputValidatingGuardrail.validationPassed).isFalse();

        // Result should contain error from guardrail
        assertThat(result).contains("Output validation failed");
        assertThat(result).contains("exceeds maximum allowed value");
    }

    @Test
    @ActivateRequestContext
    void outputGuardrail_canAccessComplexNestedArguments() {
        String ignored = aiService.chat("test", "complexTool - config1 - true");

        assertThat(ArgumentAccessingGuardrail.executed).isTrue();
        assertThat(ArgumentAccessingGuardrail.parsedArguments).isNotNull();

        // Verify guardrail could access complex parameters
        assertThat(ArgumentAccessingGuardrail.configNameFromArgs).isEqualTo("config1");
        assertThat(ArgumentAccessingGuardrail.enabledFromArgs).isTrue();
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(MyTools.class)
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @Singleton
    public static class MyTools {
        boolean processDataExecuted = false;
        boolean calculateValueExecuted = false;
        boolean complexToolExecuted = false;

        @Tool("Process data with min and max values")
        @ToolOutputGuardrails({ ArgumentAccessingGuardrail.class })
        public String processData(int maxValue, int minValue) {
            processDataExecuted = true;
            return "Processed: max=" + maxValue + ", min=" + minValue;
        }

        @Tool("Calculate a value within range")
        @ToolOutputGuardrails({ InputOutputValidatingGuardrail.class })
        public String calculateValue(int maxValue, int minValue) {
            calculateValueExecuted = true;
            // Simulate calculation that might exceed max
            // When max=100 and min=50, return 75 (valid)
            // When max=100 and min=10, return 150 (exceeds max - invalid)
            int calculatedValue = (maxValue == 100 && minValue == 10) ? 150 : 75;
            return "Value: " + calculatedValue;
        }

        @Tool("Complex tool with nested parameters")
        @ToolOutputGuardrails({ ArgumentAccessingGuardrail.class })
        public String complexTool(String configName, boolean enabled) {
            complexToolExecuted = true;
            return "Config: " + configName + ", enabled=" + enabled;
        }

        void reset() {
            processDataExecuted = false;
            calculateValueExecuted = false;
            complexToolExecuted = false;
        }
    }

    @ApplicationScoped
    public static class ArgumentAccessingGuardrail implements ToolOutputGuardrail {
        static boolean executed = false;
        static String rawArguments = null;
        static JsonObject parsedArguments = null;
        static Integer maxValueFromArgs = null;
        static Integer minValueFromArgs = null;
        static String configNameFromArgs = null;
        static Boolean enabledFromArgs = null;

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executed = true;

            // Test 1: Access raw arguments string
            if (request.executionRequest() != null) {
                rawArguments = request.executionRequest().arguments();
            }

            // Test 2: Access arguments as parsed JsonObject
            parsedArguments = request.argumentsAsJson();

            if (parsedArguments != null) {
                // Extract different types of parameters to test parsing
                if (parsedArguments.containsKey("maxValue")) {
                    maxValueFromArgs = parsedArguments.getInteger("maxValue");
                    minValueFromArgs = parsedArguments.getInteger("minValue");
                }
                if (parsedArguments.containsKey("configName")) {
                    configNameFromArgs = parsedArguments.getString("configName");
                    enabledFromArgs = parsedArguments.getBoolean("enabled");
                }
            }

            return ToolOutputGuardrailResult.success();
        }

        static void reset() {
            executed = false;
            rawArguments = null;
            parsedArguments = null;
            maxValueFromArgs = null;
            minValueFromArgs = null;
            configNameFromArgs = null;
            enabledFromArgs = null;
        }
    }

    @ApplicationScoped
    public static class InputOutputValidatingGuardrail implements ToolOutputGuardrail {
        static boolean executed = false;
        static boolean validationPassed = false;

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executed = true;
            validationPassed = false;

            // Get input parameters
            JsonObject args = request.argumentsAsJson();
            if (args == null) {
                return ToolOutputGuardrailResult.failure("Cannot access input arguments");
            }

            int maxValue = args.getInteger("maxValue");
            int minValue = args.getInteger("minValue");

            // Parse output value
            String resultText = request.resultText();
            // Extract number from "Value: 75" format
            if (!resultText.contains("Value: ")) {
                return ToolOutputGuardrailResult.failure("Invalid output format: " + resultText);
            }

            String valueStr = resultText.substring(resultText.indexOf("Value: ") + 7).trim();
            // Remove any quotes if present
            valueStr = valueStr.replaceAll("\"", "");
            int outputValue = Integer.parseInt(valueStr);

            // Validate output is within input constraints
            if (outputValue < minValue) {
                return ToolOutputGuardrailResult.failure(
                        "Output validation failed: value " + outputValue +
                                " is below minimum allowed value " + minValue);
            }

            if (outputValue > maxValue) {
                return ToolOutputGuardrailResult.failure(
                        "Output validation failed: value " + outputValue +
                                " exceeds maximum allowed value " + maxValue);
            }

            validationPassed = true;
            return ToolOutputGuardrailResult.success();
        }

        static void reset() {
            executed = false;
            validationPassed = false;
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                // Parse: "toolName - param1 - param2"
                String text = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                String[] segments = text.split(" - ");
                String toolName = segments[0];

                String arguments;
                if (toolName.equals("processData") || toolName.equals("calculateValue")) {
                    int maxValue = Integer.parseInt(segments[1]);
                    int minValue = Integer.parseInt(segments[2]);
                    arguments = String.format("{\"maxValue\":%d,\"minValue\":%d}", maxValue, minValue);
                } else if (toolName.equals("complexTool")) {
                    String configName = segments[1];
                    boolean enabled = Boolean.parseBoolean(segments[2]);
                    arguments = String.format("{\"configName\":\"%s\",\"enabled\":%b}", configName, enabled);
                } else {
                    arguments = "{}";
                }

                return ChatResponse.builder()
                        .aiMessage(new AiMessage("executing tool", List.of(ToolExecutionRequest.builder()
                                .id("tool-id-1")
                                .name(toolName)
                                .arguments(arguments)
                                .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage last = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + last.text()))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("Unexpected"))
                    .build();
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> new NoopChatMemory();
        }
    }
}

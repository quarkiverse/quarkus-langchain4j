package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.test.QuarkusUnitTest;

class ToolExecutorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    TestTool testTool = new TestTool();
    DefaultValueTool defaultValueTool = new DefaultValueTool();

    public enum SortBy {
        RELEVANCE,
        DATE,
        RATING
    }

    private static class DefaultValueTool {

        @Tool
        String withDefaultInt(String query, @P(defaultValue = "10") int limit) {
            return query + ":" + limit;
        }

        @Tool
        String withDefaultString(String query, @P(defaultValue = "USD") String currency) {
            return query + ":" + currency;
        }

        @Tool
        String withDefaultEnum(String query, @P(defaultValue = "RELEVANCE") SortBy sortBy) {
            return query + ":" + sortBy;
        }

        @Tool
        String withDefaultBoolean(String query, @P(defaultValue = "true") boolean verbose) {
            return query + ":" + verbose;
        }

        @Tool
        String withMultipleDefaults(@P(defaultValue = "5") int limit, @P(defaultValue = "DESC") String order) {
            return limit + ":" + order;
        }
    }

    private static class TestTool {

        @Tool
        double doubles(double arg0, Double arg1) {
            return arg0 + arg1;
        }

        @Tool
        float floats(float arg0, Float arg1) {
            return arg0 + arg1;
        }

        @Tool
        BigDecimal bigDecimals(BigDecimal arg0, BigDecimal arg1) {
            return arg0.add(arg1);
        }

        @Tool
        long longs(long arg0, Long arg1) {
            return arg0 + arg1;
        }

        @Tool
        int ints(int arg0, Integer arg1) {
            return arg0 + arg1;
        }

        @Tool
        short shorts(short arg0, Short arg1) {
            return (short) (arg0 + arg1);
        }

        @Tool
        byte bytes(byte arg0, Byte arg1) {
            return (byte) (arg0 + arg1);
        }

        @Tool
        BigInteger bigIntegers(BigInteger arg0, BigInteger arg1) {
            return arg0.add(arg1);
        }

        @Tool
        int noArgs() {
            return 1;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_with_parameters_of_type_double(String arguments) {
        executeAndAssert(arguments, "doubles", "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_with_parameters_of_type_float(String arguments) {
        executeAndAssert(arguments, "floats", "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Float.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    @Disabled("not implemented yet")
    void should_fail_when_argument_does_not_fit_into_float_type(String arguments) {
        executeAndExpectFailure(arguments, "floats");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_with_parameters_of_type_BigDecimal(String arguments) {
        executeAndAssert(arguments, "bigDecimals", "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_long(String arguments) {
        executeAndAssert(arguments, "longs", "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2.1}",
            "{\"arg0\": 2.1, \"arg1\": 2}"
    })
    @Disabled("not implemented yet")
    void should_fail_when_argument_is_fractional_number_for_parameter_of_type_long(String arguments) {
        executeAndExpectFailure(arguments, "longs");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_long_type(String arguments) {
        executeAndExpectFailure(arguments, "longs");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_int(String arguments) {
        executeAndAssert(arguments, "ints", "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2.1}",
            "{\"arg0\": 2.1, \"arg1\": 2}"
    })
    @Disabled("not implemented yet")
    void should_fail_when_argument_is_fractional_number_for_parameter_of_type_int(String arguments) {
        executeAndExpectFailure(arguments, "ints");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_int_type(String arguments) {
        executeAndExpectFailure(arguments, "ints");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_short(String arguments) {
        executeAndAssert(arguments, "shorts", "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2.1}",
            "{\"arg0\": 2.1, \"arg1\": 2}"
    })
    @Disabled("not implemented yet")
    void should_fail_when_argument_is_fractional_number_for_parameter_of_type_short(String arguments) {
        executeAndExpectFailure(arguments, "shorts");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_short_type(String arguments) {
        executeAndExpectFailure(arguments, "shorts");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_byte(String arguments) {
        executeAndAssert(arguments, "bytes", "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2.1}",
            "{\"arg0\": 2.1, \"arg1\": 2}"
    })
    @Disabled("not implemented yet")
    void should_fail_when_argument_is_fractional_number_for_parameter_of_type_byte(String arguments) {
        executeAndExpectFailure(arguments, "bytes");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_byte_type(String arguments) {
        executeAndExpectFailure(arguments, "bytes");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_BigInteger(String arguments) {
        executeAndAssert(arguments, "bigIntegers", "4");
    }

    @Test
    void should_execute_tool_with_no_args() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .build();

        ToolExecutor toolExecutor = getToolExecutor("noArgs");

        String result = toolExecutor.execute(request, null);

        assertThat(result).isEqualTo("1");
    }

    @Test
    void should_use_default_value_for_omitted_int_parameter() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments("{\"query\": \"test\"}")
                .build();

        ToolExecutor toolExecutor = getToolExecutor("withDefaultInt", DefaultValueTool.class, defaultValueTool);

        String result = toolExecutor.execute(request, null);

        assertThat(result).isEqualTo("\"test:10\"");
    }

    @Test
    void should_use_provided_value_over_default_for_int_parameter() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments("{\"query\": \"test\", \"limit\": 25}")
                .build();

        ToolExecutor toolExecutor = getToolExecutor("withDefaultInt", DefaultValueTool.class, defaultValueTool);

        String result = toolExecutor.execute(request, null);

        assertThat(result).isEqualTo("\"test:25\"");
    }

    @Test
    void should_use_default_value_for_omitted_string_parameter() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments("{\"query\": \"test\"}")
                .build();

        ToolExecutor toolExecutor = getToolExecutor("withDefaultString", DefaultValueTool.class, defaultValueTool);

        String result = toolExecutor.execute(request, null);

        assertThat(result).isEqualTo("\"test:USD\"");
    }

    @Test
    void should_use_default_value_for_omitted_enum_parameter() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments("{\"query\": \"test\"}")
                .build();

        ToolExecutor toolExecutor = getToolExecutor("withDefaultEnum", DefaultValueTool.class, defaultValueTool);

        String result = toolExecutor.execute(request, null);

        assertThat(result).isEqualTo("\"test:RELEVANCE\"");
    }

    @Test
    void should_use_default_value_for_omitted_boolean_parameter() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments("{\"query\": \"test\"}")
                .build();

        ToolExecutor toolExecutor = getToolExecutor("withDefaultBoolean", DefaultValueTool.class, defaultValueTool);

        String result = toolExecutor.execute(request, null);

        assertThat(result).isEqualTo("\"test:true\"");
    }

    @Test
    void should_use_default_values_when_all_parameters_have_defaults_and_all_omitted() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments("{}")
                .build();

        ToolExecutor toolExecutor = getToolExecutor("withMultipleDefaults", DefaultValueTool.class, defaultValueTool);

        String result = toolExecutor.execute(request, null);

        assertThat(result).isEqualTo("\"5:DESC\"");
    }

    @Test
    void should_mark_parameter_with_default_as_not_required_in_tool_specification() {
        List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata()
                .get(DefaultValueTool.class.getName());
        assertThat(methodCreateInfos).isNotNull();

        ToolMethodCreateInfo withDefaultInt = methodCreateInfos.stream()
                .filter(m -> m.toolSpecification().name().equals("withDefaultInt"))
                .findFirst().orElseThrow();

        JsonObjectSchema schema = withDefaultInt.toolSpecification().parameters();
        assertThat(schema.required()).containsExactly("query");
        assertThat(schema.properties().keySet()).containsExactlyInAnyOrder("query", "limit");
    }

    private void executeAndAssert(String arguments, String methodName,
            String expectedResult) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments(arguments)
                .build();

        ToolExecutor toolExecutor = getToolExecutor(methodName);

        String result = toolExecutor.execute(request, null);

        assertThat(result).isEqualTo(expectedResult);
    }

    private void executeAndExpectFailure(String arguments, String methodName) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments(arguments)
                .build();

        ToolExecutor toolExecutor = getToolExecutor(methodName);

        assertThatThrownBy(() -> toolExecutor.execute(request, null))
                .isExactlyInstanceOf(ToolArgumentsException.class);
    }

    private ToolExecutor getToolExecutor(String methodName) {
        return getToolExecutor(methodName, TestTool.class, testTool);
    }

    private ToolExecutor getToolExecutor(String methodName, Class<?> toolClass, Object toolInstance) {

        List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata().get(toolClass.getName());
        if (methodCreateInfos == null) {
            fail("Unable to find necessary metadata for class: " + toolClass.getSimpleName());
            return null; // keep the compiler happy
        }

        ToolExecutor toolExecutor = null;
        for (ToolMethodCreateInfo methodCreateInfo : methodCreateInfos) {
            String invokerClassName = methodCreateInfo.invokerClassName();
            ToolSpecification toolSpecification = methodCreateInfo.toolSpecification();
            if (methodName.equals(toolSpecification.name())) {
                toolExecutor = new QuarkusToolExecutor(
                        new QuarkusToolExecutor.Context(toolInstance, invokerClassName, methodCreateInfo.methodName(),
                                methodCreateInfo.argumentMapperClassName(), methodCreateInfo.executionModel(),
                                methodCreateInfo.returnBehavior(), false, methodCreateInfo));
                break;
            }
        }

        if (toolExecutor == null) {
            fail("Unable to find QuarkusToolExecutor for method : " + methodName);
            return null; // keep the compiler happy
        }

        return toolExecutor;
    }
}

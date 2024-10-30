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

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
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
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    private ToolExecutor getToolExecutor(String methodName) {

        List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata().get(TestTool.class.getName());
        if (methodCreateInfos == null) {
            fail("Unable to find necessary metadata for class: " + TestTool.class.getSimpleName());
            return null; // keep the compiler happy
        }

        ToolExecutor toolExecutor = null;
        for (ToolMethodCreateInfo methodCreateInfo : methodCreateInfos) {
            String invokerClassName = methodCreateInfo.invokerClassName();
            ToolSpecification toolSpecification = methodCreateInfo.toolSpecification();
            if (methodName.equals(
                    toolSpecification.name())) { // this only works because TestTool does not contain overloaded methods
                toolExecutor = new QuarkusToolExecutor(
                        new QuarkusToolExecutor.Context(testTool, invokerClassName, methodCreateInfo.methodName(),
                                methodCreateInfo.argumentMapperClassName(), methodCreateInfo.executionModel()));
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

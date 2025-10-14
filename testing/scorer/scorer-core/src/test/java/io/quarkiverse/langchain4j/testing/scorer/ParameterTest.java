package io.quarkiverse.langchain4j.testing.scorer;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class ParameterTest {

    @Test
    void unnamedParameterShouldReturnValue() {
        Parameter parameter = new Parameter.UnnamedParameter("testValue");
        Object value = parameter.value();
        assertThat(value).isEqualTo("testValue");
    }

    @SuppressWarnings("CastCanBeRemovedNarrowingVariableType")
    @Test
    void namedParameterShouldReturnNameAndValue() {
        Parameter parameter = new Parameter.NamedParameter("testName", 42);
        String name = ((Parameter.NamedParameter) parameter).name();
        Object value = parameter.value();

        assertThat(name).isEqualTo("testName");
        assertThat(value).isEqualTo(42);
    }

    @Test
    void asMethodShouldReturnValueIfTypeMatches() {
        Parameter parameter = new Parameter.UnnamedParameter(123);
        Integer value = parameter.as(Integer.class);
        assertThat(value).isEqualTo(123);
    }

    @Test
    void asMethodShouldConvertValueToTargetTypeIfPossible() {
        Parameter parameter = new Parameter.UnnamedParameter("123");
        Integer value = parameter.as(Integer.class);
        assertThat(value).isEqualTo(123);
    }

    @Test
    @Disabled("Error has change in latest versions of Quarkus")
    void asMethodShouldThrowExceptionIfValueCannotBeConverted() {
        Parameter parameter = new Parameter.UnnamedParameter("notANumber");
        // When / Then
        assertThatThrownBy(() -> parameter.as(Integer.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SRCFG00020");
    }

    @Test
    void asMethodShouldThrowExceptionForMissingConverter() {
        Parameter parameter = new Parameter.UnnamedParameter("123");

        assertThatThrownBy(() -> parameter.as(List.class))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void castMethodShouldReturnCastedValue() {
        Parameter parameter = new Parameter.UnnamedParameter("testValue");
        String value = parameter.cast();
        assertThat(value).isEqualTo("testValue");
    }

    @Test
    void castMethodShouldThrowClassCastExceptionForInvalidCast() {
        Parameter parameter = new Parameter.UnnamedParameter(123);
        assertThatThrownBy(() -> parameter.as(List.class))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void namedParameterShouldStoreNameAndValueCorrectly() {
        Parameter.NamedParameter parameter = new Parameter.NamedParameter("testName", "testValue");
        String name = parameter.name();
        Object value = parameter.value();
        assertThat(name).isEqualTo("testName");
        assertThat(value).isEqualTo("testValue");
    }

    @Test
    void unnamedParameterShouldStoreValueCorrectly() {
        Parameter.UnnamedParameter parameter = new Parameter.UnnamedParameter("testValue");
        Object value = parameter.value();
        assertThat(value).isEqualTo("testValue");
    }

    @Test
    void unnamedParameterShouldAllowNullValue() {
        Parameter.UnnamedParameter parameter = new Parameter.UnnamedParameter(null);
        Object value = parameter.value();
        assertThat(value).isNull();
    }

    @Test
    void namedParameterShouldAllowNullValue() {
        Parameter.NamedParameter parameter = new Parameter.NamedParameter("testName", null);
        Object value = parameter.value();
        assertThat(value).isNull();
    }

    @Test
    void namedParameterShouldThrowExceptionForNullName() {
        assertThatThrownBy(() -> new Parameter.NamedParameter(null, "testValue"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name");
    }
}

package io.quarkiverse.langchain4j.testing.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Iterator;

import org.junit.jupiter.api.Test;

class ParametersTest {

    @Test
    void ofShouldCreateParametersWithUnnamedValues() {
        Parameters parameters = Parameters.of(1, "test", 3.14);
        assertThat(parameters.size()).isEqualTo(3);
        assertThat((Integer) parameters.get(0)).isEqualTo(1);
        assertThat((String) parameters.get(1)).isEqualTo("test");
        assertThat((double) parameters.get(2)).isEqualTo(3.14);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void ofShouldThrowExceptionForNullValues() {
        assertThatThrownBy(() -> Parameters.of((Object[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Values must not be null");
    }

    @Test
    void ofShouldThrowExceptionForEmptyValues() {
        assertThatThrownBy(Parameters::of)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Values must not be empty");
    }

    @Test
    void getByIndexShouldReturnCorrectValue() {
        Parameters parameters = Parameters.of(1, "test", 3.14);
        Integer intValue = parameters.get(0);
        String stringValue = parameters.get(1);
        Double doubleValue = parameters.get(2);
        assertThat(intValue).isEqualTo(1);
        assertThat(stringValue).isEqualTo("test");
        assertThat(doubleValue).isEqualTo(3.14);
    }

    @Test
    void getByIndexShouldThrowExceptionForInvalidIndex() {
        Parameters parameters = Parameters.of(1, "test", 3.14);
        assertThatThrownBy(() -> parameters.get(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> parameters.get(3))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void getByIndexWithTypeShouldConvertValue() {
        Parameters parameters = Parameters.of("123");
        Integer value = parameters.get(0, Integer.class);
        assertThat(value).isEqualTo(123);
    }

    @Test
    void getByNameShouldReturnCorrectValue() {
        Parameters parameters = new Parameters().add("name1", 42).add("name2", "test");
        Integer intValue = parameters.get("name1");
        String stringValue = parameters.get("name2");
        assertThat(intValue).isEqualTo(42);
        assertThat(stringValue).isEqualTo("test");
    }

    @Test
    void getByNameShouldThrowExceptionIfNameNotFound() {
        Parameters parameters = new Parameters().add("name1", 42);
        assertThatThrownBy(() -> parameters.get("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parameter not found: unknown");
    }

    @Test
    void getByNameWithTypeShouldConvertValue() {
        Parameters parameters = new Parameters().add("name", "123");
        Integer value = parameters.get("name", Integer.class);
        assertThat(value).isEqualTo(123);
    }

    @Test
    void iteratorShouldIterateOverParameterValues() {
        Parameters parameters = Parameters.of(1, "test", 3.14);
        Iterator<Object> iterator = parameters.iterator();
        assertThat(iterator).toIterable()
                .containsExactly(1, "test", 3.14);
    }

    @Test
    void toArrayShouldReturnAllParameterValues() {
        Parameters parameters = Parameters.of(1, "test", 3.14);
        Object[] values = parameters.toArray();
        assertThat(values).containsExactly(1, "test", 3.14);
    }

    @Test
    void addUnnamedParameterShouldIncreaseSize() {
        Parameters parameters = new Parameters();
        parameters.add(42).add("test").add(3.14);
        assertThat(parameters.size()).isEqualTo(3);
        assertThat((int) parameters.get(0)).isEqualTo(42);
        assertThat((String) parameters.get(1)).isEqualTo("test");
        assertThat((double) parameters.get(2)).isEqualTo(3.14);
    }

    @Test
    void addNamedParameterShouldStoreNameAndValue() {
        Parameters parameters = new Parameters();
        parameters.add("name1", 42).add("name2", "test");
        assertThat(parameters.size()).isEqualTo(2);
        assertThat((int) parameters.get("name1")).isEqualTo(42);
        assertThat((String) parameters.get("name2")).isEqualTo("test");
    }

    @Test
    void addParameterShouldAcceptCustomParameter() {
        Parameters parameters = new Parameters();
        Parameter parameter = new Parameter.NamedParameter("name", 42);
        parameters.add(parameter);
        assertThat(parameters.size()).isEqualTo(1);
        assertThat(parameters.get("name", Integer.class)).isEqualTo(42);
    }
}

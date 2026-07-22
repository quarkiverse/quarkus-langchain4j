package io.quarkiverse.langchain4j.lancedb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

class LanceDbFilterMapperTest {

    private final LanceDbFilterMapper mapper = new LanceDbFilterMapper();

    @Test
    void testNullFilter() {
        assertThat(mapper.map(null)).isNull();
    }

    @Test
    void testStringEqual() {
        assertThat(mapper.map(new IsEqualTo("name", "John"))).isEqualTo("metadata.name = 'John'");
    }

    @Test
    void testStringNotEqual() {
        assertThat(mapper.map(new IsNotEqualTo("status", "active"))).isEqualTo("metadata.status != 'active'");
    }

    @Test
    void testIntegerEqual() {
        assertThat(mapper.map(new IsEqualTo("age", 25))).isEqualTo("metadata.age = 25");
    }

    @Test
    void testLongEqual() {
        assertThat(mapper.map(new IsEqualTo("id", 123L))).isEqualTo("metadata.id = 123");
    }

    @Test
    void testDoubleEqual() {
        assertThat(mapper.map(new IsEqualTo("price", 99.99))).isEqualTo("metadata.price = 99.99");
    }

    @Test
    void testStringGreaterThan() {
        assertThat(mapper.map(new IsGreaterThan("name", "A"))).isEqualTo("metadata.name > 'A'");
    }

    @Test
    void testIntegerGreaterThan() {
        assertThat(mapper.map(new IsGreaterThan("age", 18))).isEqualTo("metadata.age > 18");
    }

    @Test
    void testGreaterThanOrEqual() {
        assertThat(mapper.map(new IsGreaterThanOrEqualTo("age", 21))).isEqualTo("metadata.age >= 21");
    }

    @Test
    void testStringLessThan() {
        assertThat(mapper.map(new IsLessThan("name", "Z"))).isEqualTo("metadata.name < 'Z'");
    }

    @Test
    void testFloatLessThan() {
        assertThat(mapper.map(new IsLessThan("score", 4.5f))).isEqualTo("metadata.score < 4.5");
    }

    @Test
    void testLessThanOrEqual() {
        assertThat(mapper.map(new IsLessThanOrEqualTo("price", 100.0))).isEqualTo("metadata.price <= 100.0");
    }

    @Test
    void testStringIn() {
        assertThat(mapper.map(new IsIn("category", Arrays.asList("A", "B", "C"))))
                .isEqualTo("metadata.category IN ('A', 'B', 'C')");
    }

    @Test
    void testIntegerIn() {
        assertThat(mapper.map(new IsIn("status", Arrays.asList(1, 2, 3))))
                .isEqualTo("metadata.status IN (1, 2, 3)");
    }

    @Test
    void testStringNotIn() {
        assertThat(mapper.map(new IsNotIn("category", Arrays.asList("X", "Y"))))
                .isEqualTo("metadata.category NOT IN ('X', 'Y')");
    }

    @Test
    void testIntegerNotIn() {
        assertThat(mapper.map(new IsNotIn("status", Arrays.asList(0, 9))))
                .isEqualTo("metadata.status NOT IN (0, 9)");
    }

    @Test
    void testAndFilter() {
        assertThat(mapper.map(new And(new IsEqualTo("name", "John"), new IsEqualTo("age", 25))))
                .isEqualTo("(metadata.name = 'John' AND metadata.age = 25)");
    }

    @Test
    void testOrFilter() {
        assertThat(mapper.map(new Or(new IsEqualTo("name", "John"), new IsEqualTo("name", "Jane"))))
                .isEqualTo("(metadata.name = 'John' OR metadata.name = 'Jane')");
    }

    @Test
    void testNotFilter() {
        assertThat(mapper.map(new Not(new IsEqualTo("status", "inactive"))))
                .isEqualTo("NOT (metadata.status = 'inactive')");
    }

    @Test
    void testComplexNestedFilter() {
        assertThat(mapper.map(new And(
                new IsEqualTo("category", "book"),
                new Or(new IsGreaterThan("price", 10.0), new IsLessThan("price", 5.0)))))
                .isEqualTo("(metadata.category = 'book' AND (metadata.price > 10.0 OR metadata.price < 5.0))");
    }

    @Test
    void testStringEscaping() {
        assertThat(mapper.map(new IsEqualTo("name", "O'Brien")))
                .isEqualTo("metadata.name = 'O\\'Brien'");
    }

    @Test
    void testUnsupportedFilter() {
        Filter unsupportedFilter = object -> false;
        assertThatThrownBy(() -> mapper.map(unsupportedFilter))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported filter type:");
    }
}

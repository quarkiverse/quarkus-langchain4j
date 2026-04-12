package io.quarkiverse.langchain4j.infinispan;

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

class InfinispanMetadataFilterMapperTest {

    @Test
    void testNullFilter() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper().map(null);
        assertThat(result).isNull();
    }

    @Test
    void testStringEqual() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsEqualTo("name", "John"));
        assertThat(result.query).isEqualTo("m0.name='name' and m0.value = 'John'");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testStringNotEqual() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsNotEqualTo("status", "active"));
        assertThat(result.query).isEqualTo("m0.value != 'active' and m0.name='status' OR (i.metadata is null) ");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testStringGreaterThan() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsGreaterThan("name", "A"));
        assertThat(result.query).isEqualTo("m0.name='name' and m0.value > 'A'");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testStringGreaterThanOrEqual() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsGreaterThanOrEqualTo("name", "A"));
        assertThat(result.query).isEqualTo("m0.name='name' and m0.value >= 'A'");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testStringLessThan() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsLessThan("name", "Z"));
        assertThat(result.query).isEqualTo("m0.name='name' and m0.value < 'Z'");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testStringLessThanOrEqual() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsLessThanOrEqualTo("name", "Z"));
        assertThat(result.query).isEqualTo("m0.name='name' and m0.value <= 'Z'");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testIntegerEqual() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsEqualTo("age", 25));
        assertThat(result.query).isEqualTo("m0.name='age' and m0.value_int = 25");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testLongEqual() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsEqualTo("id", 123L));
        assertThat(result.query).isEqualTo("m0.name='id' and m0.value_int = 123");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testDoubleEqual() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsEqualTo("price", 99.99));
        assertThat(result.query).isEqualTo("m0.name='price' and m0.value_float = 99.99");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testIntegerGreaterThan() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsGreaterThan("age", 18));
        assertThat(result.query).isEqualTo("m0.name='age' and m0.value_int > 18");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testFloatLessThan() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsLessThan("score", 4.5f));
        assertThat(result.query).isEqualTo("m0.name='score' and m0.value_float < 4.5");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testStringIn() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsIn("category", Arrays.asList("A", "B", "C")));
        assertThat(result.query).isEqualTo("m0.name='category' and m0.value IN ('A', 'B', 'C')");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testIntegerIn() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsIn("status", Arrays.asList(1, 2, 3)));
        assertThat(result.query).isEqualTo("m0.name='status' and m0.value_int IN (1, 2, 3)");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testStringNotIn() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsNotIn("category", Arrays.asList("X", "Y")));
        assertThat(result.query).isEqualTo(
                "(m0.value NOT IN ('X', 'Y') and m0.name='category') OR (m0.value IN ('X', 'Y') and m0.name!='category') OR (i.metadata is null) ");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testIntegerNotIn() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsNotIn("status", Arrays.asList(0, 9)));
        assertThat(result.query).isEqualTo(
                "(m0.value_int NOT IN (0, 9) and m0.name='status') OR (m0.value_int IN (0, 9) and m0.name!='status') OR (i.metadata is null) ");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testAndFilter() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new And(new IsEqualTo("name", "John"), new IsEqualTo("age", 25)));
        assertThat(result.query)
                .isEqualTo("((m0.name='name' and m0.value = 'John') AND (m1.name='age' and m1.value_int = 25))");
        assertThat(result.join).isEqualTo(" join i.metadata m0 join i.metadata m1");
    }

    @Test
    void testOrFilter() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new Or(new IsEqualTo("name", "John"), new IsEqualTo("name", "Jane")));
        assertThat(result.query)
                .isEqualTo("((m0.name='name' and m0.value = 'John') OR (m1.name='name' and m1.value = 'Jane'))");
        assertThat(result.join).isEqualTo(" join i.metadata m0 join i.metadata m1");
    }

    @Test
    void testNotFilter() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new Not(new IsEqualTo("status", "inactive")));
        assertThat(result.query).isEqualTo("(NOT (m0.name='status' and m0.value = 'inactive'))");
        assertThat(result.join).isEqualTo(" join i.metadata m0");
    }

    @Test
    void testComplexNestedFilter() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new And(
                        new IsEqualTo("category", "book"),
                        new Or(new IsGreaterThan("price", 10.0), new IsLessThan("price", 5.0))));
        assertThat(result.query).isEqualTo(
                "((m0.name='category' and m0.value = 'book') AND (((m1.name='price' and m1.value_float > 10.0) OR (m2.name='price' and m2.value_float < 5.0))))");
        assertThat(result.join).isEqualTo(" join i.metadata m0 join i.metadata m1 join i.metadata m2");
    }

    @Test
    void testMultipleMetadataJoins() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new And(
                        new IsEqualTo("name", "John"),
                        new And(
                                new IsEqualTo("age", 25),
                                new And(new IsEqualTo("city", "New York"), new IsEqualTo("country", "USA")))));
        assertThat(result.join)
                .isEqualTo(" join i.metadata m0 join i.metadata m1 join i.metadata m2 join i.metadata m3");
        assertThat(result.query).isEqualTo(
                "((m0.name='name' and m0.value = 'John') AND (((m1.name='age' and m1.value_int = 25) AND (((m2.name='city' and m2.value = 'New York') AND (m3.name='country' and m3.value = 'USA'))))))");
    }

    @Test
    void testLargeNumbers() {
        InfinispanMetadataFilterMapper.FilterResult result = new InfinispanMetadataFilterMapper()
                .map(new IsEqualTo("id", Long.MAX_VALUE));
        assertThat(result.query).isEqualTo("m0.name='id' and m0.value_int = " + Long.MAX_VALUE);
    }

    @Test
    void testUnsupportedFilter() {
        Filter unsupportedFilter = object -> false;

        assertThatThrownBy(() -> new InfinispanMetadataFilterMapper().map(unsupportedFilter))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported filter type:");
    }
}

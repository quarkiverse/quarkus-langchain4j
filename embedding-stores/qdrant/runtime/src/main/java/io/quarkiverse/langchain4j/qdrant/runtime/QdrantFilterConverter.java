package io.quarkiverse.langchain4j.qdrant.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

/**
 * Converts a langchain4j {@link Filter} into the JSON structure understood by the Qdrant REST API
 * <a href="https://qdrant.tech/documentation/concepts/filtering/">filter</a> clause, as a {@link Map} ready to be
 * passed to the quarkus-qdrant search request.
 * <p>
 * An operator that cannot be translated raises {@link UnsupportedOperationException} rather than being dropped, so a
 * filter is either applied in full or the search fails. {@code IsNotIn} is rendered as {@code must_not} over
 * {@code match.any} (not {@code match.except}) so that a document missing the key is kept, which is what langchain4j
 * considers "not in". {@code ContainsString} is rejected: Qdrant's {@code match.text} is tokenizer- and
 * index-dependent and cannot reproduce its case-sensitive substring semantics.
 */
final class QdrantFilterConverter {

    private QdrantFilterConverter() {
    }

    static Map<String, Object> convert(Filter filter) {
        return convertOperand(filter);
    }

    private static Map<String, Object> convertOperand(Filter operand) {
        List<Object> must = new ArrayList<>();
        List<Object> should = new ArrayList<>();
        List<Object> mustNot = new ArrayList<>();

        if (operand instanceof Not not) {
            mustNot.add(convertOperand(not.expression()));
        } else if (operand instanceof And and) {
            must.add(convertOperand(and.left()));
            must.add(convertOperand(and.right()));
        } else if (operand instanceof Or or) {
            should.add(convertOperand(or.left()));
            should.add(convertOperand(or.right()));
        } else {
            must.add(convertCondition(operand));
        }

        Map<String, Object> filter = new LinkedHashMap<>();
        if (!must.isEmpty()) {
            filter.put("must", must);
        }
        if (!should.isEmpty()) {
            filter.put("should", should);
        }
        if (!mustNot.isEmpty()) {
            filter.put("must_not", mustNot);
        }
        return filter;
    }

    private static Map<String, Object> convertCondition(Filter filter) {
        if (filter instanceof IsEqualTo isEqualTo) {
            return field(isEqualTo.key(), match("value", matchValue(isEqualTo.comparisonValue())));
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return mustNot(field(isNotEqualTo.key(), match("value", matchValue(isNotEqualTo.comparisonValue()))));
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return field(isGreaterThan.key(), range("gt", number(isGreaterThan.comparisonValue())));
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return field(isGreaterThanOrEqualTo.key(), range("gte", number(isGreaterThanOrEqualTo.comparisonValue())));
        } else if (filter instanceof IsLessThan isLessThan) {
            return field(isLessThan.key(), range("lt", number(isLessThan.comparisonValue())));
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return field(isLessThanOrEqualTo.key(), range("lte", number(isLessThanOrEqualTo.comparisonValue())));
        } else if (filter instanceof IsIn isIn) {
            return field(isIn.key(), match("any", values(isIn.comparisonValues())));
        } else if (filter instanceof IsNotIn isNotIn) {
            return mustNot(field(isNotIn.key(), match("any", values(isNotIn.comparisonValues()))));
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static Map<String, Object> field(String key, Map<String, Object> condition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.putAll(condition);
        return result;
    }

    private static Map<String, Object> match(String operator, Object value) {
        return Map.of("match", Map.of(operator, value));
    }

    private static Map<String, Object> range(String operator, Object value) {
        return Map.of("range", Map.of(operator, value));
    }

    private static Map<String, Object> mustNot(Map<String, Object> condition) {
        return Map.of("must_not", List.of(condition));
    }

    private static Object matchValue(Object value) {
        if (value instanceof String || value instanceof UUID) {
            return value.toString();
        } else if (value instanceof Boolean booleanValue) {
            return booleanValue;
        } else if (value instanceof Integer || value instanceof Long) {
            return Long.parseLong(value.toString());
        }
        throw new IllegalArgumentException(
                "Unsupported value type for equality filter, only String, UUID, Boolean, Integer and Long are supported: "
                        + value.getClass().getName());
    }

    private static Object number(Object value) {
        if (value instanceof Number) {
            return Double.parseDouble(value.toString());
        }
        throw new IllegalArgumentException(
                "Unsupported value type for range filter, only numbers are supported: " + value.getClass().getName());
    }

    private static List<Object> values(Collection<?> comparisonValues) {
        List<?> valueList = new ArrayList<>(comparisonValues);
        if (valueList.isEmpty()) {
            return List.of();
        }
        Object first = valueList.get(0);
        List<Object> result = new ArrayList<>(valueList.size());
        if (first instanceof String || first instanceof UUID) {
            for (Object value : valueList) {
                result.add(value.toString());
            }
        } else if (first instanceof Integer || first instanceof Long) {
            for (Object value : valueList) {
                result.add(Long.parseLong(value.toString()));
            }
        } else {
            throw new IllegalArgumentException(
                    "Unsupported value type in filter value list, only String, UUID, Integer and Long are supported: "
                            + first.getClass().getName());
        }
        return result;
    }
}

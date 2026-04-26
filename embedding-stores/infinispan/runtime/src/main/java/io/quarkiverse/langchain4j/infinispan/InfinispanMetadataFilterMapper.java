package io.quarkiverse.langchain4j.infinispan;

import java.util.Collection;
import java.util.stream.Collectors;

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
 * Maps langchain4j {@link Filter} objects to Infinispan Ickle query predicates.
 * <p>
 * Metadata is stored as embedded objects with typed fields: {@code value} (String),
 * {@code value_int} (Long), and {@code value_float} (Double). The mapper generates
 * JOIN clauses and filtering predicates that operate on these typed fields.
 * <p>
 * Each comparison filter requires a separate JOIN alias (m0, m1, m2, ...).
 * Logical filters (AND, OR, NOT) do not consume aliases themselves.
 */
class InfinispanMetadataFilterMapper {

    private int joinIndex = -1;

    static class FilterResult {
        final String join;
        final String query;

        FilterResult(String query, int maxJoinIndex) {
            this.query = query;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j <= maxJoinIndex; j++) {
                sb.append(" join i.metadata m").append(j);
            }
            this.join = sb.toString();
        }
    }

    FilterResult map(Filter filter) {
        if (filter == null) {
            return null;
        }
        return new FilterResult(toQueryString(filter), joinIndex);
    }

    private String toQueryString(Filter filter) {
        if (filter instanceof IsEqualTo f) {
            return mapComparison(f.key(), "=", f.comparisonValue());
        } else if (filter instanceof IsNotEqualTo f) {
            return mapNegatedComparison(f.key(), "!=", f.comparisonValue());
        } else if (filter instanceof IsGreaterThan f) {
            return mapComparison(f.key(), ">", f.comparisonValue());
        } else if (filter instanceof IsGreaterThanOrEqualTo f) {
            return mapComparison(f.key(), ">=", f.comparisonValue());
        } else if (filter instanceof IsLessThan f) {
            return mapComparison(f.key(), "<", f.comparisonValue());
        } else if (filter instanceof IsLessThanOrEqualTo f) {
            return mapComparison(f.key(), "<=", f.comparisonValue());
        } else if (filter instanceof IsIn f) {
            return mapIn(f);
        } else if (filter instanceof IsNotIn f) {
            return mapNotIn(f);
        } else if (filter instanceof And f) {
            return "((" + toQueryString(f.left()) + ") AND (" + toQueryString(f.right()) + "))";
        } else if (filter instanceof Or f) {
            return "((" + toQueryString(f.left()) + ") OR (" + toQueryString(f.right()) + "))";
        } else if (filter instanceof Not f) {
            return "(NOT (" + toQueryString(f.expression()) + "))";
        }
        throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
    }

    private String mapComparison(String key, String operator, Object value) {
        String alias = nextAlias();
        return alias + "name='" + key + "' and " + alias + valueField(value) + " " + operator + " " + formatValue(value);
    }

    private String mapNegatedComparison(String key, String operator, Object value) {
        String alias = nextAlias();
        return alias + valueField(value) + " " + operator + " " + formatValue(value)
                + " and " + alias + "name='" + key + "'"
                + " OR (i.metadata is null) ";
    }

    private String mapIn(IsIn filter) {
        Object sample = firstElement(filter.comparisonValues());
        String alias = nextAlias();
        String field = valueField(sample);
        String values = formatCollection(filter.comparisonValues(), sample instanceof Number);
        return alias + "name='" + filter.key() + "' and " + alias + field + " IN (" + values + ")";
    }

    private String mapNotIn(IsNotIn filter) {
        Object sample = firstElement(filter.comparisonValues());
        String alias = nextAlias();
        String field = valueField(sample);
        String values = formatCollection(filter.comparisonValues(), sample instanceof Number);
        return "(" + alias + field + " NOT IN (" + values + ") and " + alias + "name='" + filter.key() + "')"
                + " OR (" + alias + field + " IN (" + values + ") and " + alias + "name!='" + filter.key() + "')"
                + " OR (i.metadata is null) ";
    }

    private String nextAlias() {
        joinIndex++;
        return "m" + joinIndex + ".";
    }

    private static String valueField(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return "value_int";
        } else if (value instanceof Float || value instanceof Double) {
            return "value_float";
        }
        return "value";
    }

    private static String formatValue(Object value) {
        if (value instanceof Integer) {
            return String.valueOf(((Integer) value).longValue());
        } else if (value instanceof Long) {
            return String.valueOf(value);
        } else if (value instanceof Float) {
            return String.valueOf(((Float) value).doubleValue());
        } else if (value instanceof Double) {
            return String.valueOf(value);
        }
        return "'" + value + "'";
    }

    private static String formatCollection(Collection<?> values, boolean numeric) {
        return values.stream()
                .map(v -> numeric ? v.toString() : "'" + v + "'")
                .collect(Collectors.joining(", "));
    }

    private static Object firstElement(Collection<?> values) {
        return values.stream().findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "Infinispan metadata filter IN must contain values"));
    }
}

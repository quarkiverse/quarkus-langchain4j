package io.quarkiverse.langchain4j.pgvector;

import static java.lang.String.format;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import io.quarkus.logging.Log;

class JSONFilterMapper {
    final String metadataColumn;

    public JSONFilterMapper(String metadataColumn) {
        this.metadataColumn = metadataColumn;
    }

    String map(Filter filter) {
        if (filter instanceof IsEqualTo) {
            return mapEqual((IsEqualTo) filter);
        } else if (filter instanceof IsNotEqualTo) {
            return mapNotEqual((IsNotEqualTo) filter);
        } else if (filter instanceof IsGreaterThan) {
            return mapGreaterThan((IsGreaterThan) filter);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter);
        } else if (filter instanceof IsLessThan) {
            return mapLessThan((IsLessThan) filter);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return mapLessThanOrEqual((IsLessThanOrEqualTo) filter);
        } else if (filter instanceof IsIn) {
            return mapIn((IsIn) filter);
        } else if (filter instanceof IsNotIn) {
            return mapNotIn((IsNotIn) filter);
        } else if (filter instanceof And) {
            return mapAnd((And) filter);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapEqual(IsEqualTo isEqualTo) {
        String key = formatKey(isEqualTo.key(), isEqualTo.comparisonValue().getClass());
        return format("%s is not null and %s = %s", key, key,
                formatValue(isEqualTo.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        String key = formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue().getClass());
        return format("%s is null or %s != %s", key, key,
                formatValue(isNotEqualTo.comparisonValue()));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return format("%s > %s", formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue().getClass()),
                formatValue(isGreaterThan.comparisonValue()));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return format("%s >= %s", formatKey(isGreaterThanOrEqualTo.key(), isGreaterThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return format("%s < %s", formatKey(isLessThan.key(), isLessThan.comparisonValue().getClass()),
                formatValue(isLessThan.comparisonValue()));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return format("%s <= %s", formatKey(isLessThanOrEqualTo.key(), isLessThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    public String mapIn(IsIn isIn) {
        return format("%s in %s", formatKeyAsText(isIn.key()), formatValuesAsString(isIn.comparisonValues()));
    }

    public String mapNotIn(IsNotIn isNotIn) {
        String key = formatKeyAsText(isNotIn.key());
        return format("%s is null or %s not in %s", key, key, formatValuesAsString(isNotIn.comparisonValues()));
    }

    private String mapAnd(And and) {
        return format("%s and %s", map(and.left()), map(and.right()));
    }

    private String mapNot(Not not) {
        return format("not(%s)", map(not.expression()));
    }

    private String mapOr(Or or) {
        return format("(%s or %s)", map(or.left()), map(or.right()));
    }

    private String formatKeyAsText(String key) {
        return metadataColumn + "->>'" + key + "'";
    }

    private String formatKey(String key, Class<?> valueType) {
        Log.debugf("formatKey %s -> %s", key, valueType);
        return String.format("(%s->>'%s')::%s", metadataColumn, key, castMap.get(valueType));
    }

    private final Map<Class<?>, String> castMap = Map.of(
            Integer.class, "int",
            Long.class, "bigint",
            Float.class, "numeric",
            Double.class, "float8",
            String.class, "text",
            Boolean.class, "boolean",
            // Default
            Object.class, "text");

    private String formatValue(Object value) {
        if (value instanceof String) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }

    private String formatValuesAsString(Collection<?> values) {
        return "(" + values.stream().map(v -> String.format("'%s'", v))
                .collect(Collectors.joining(",")) + ")";
    }
}

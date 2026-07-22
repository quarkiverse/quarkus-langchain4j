package io.quarkiverse.langchain4j.lancedb;

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

public class LanceDbFilterMapper {

    public String map(Filter filter) {
        if (filter == null) {
            return null;
        }
        return mapFilter(filter);
    }

    private String mapFilter(Filter filter) {
        if (filter instanceof IsEqualTo f) {
            return mapEqual(f);
        } else if (filter instanceof IsNotEqualTo f) {
            return mapNotEqual(f);
        } else if (filter instanceof IsGreaterThan f) {
            return mapGreaterThan(f);
        } else if (filter instanceof IsGreaterThanOrEqualTo f) {
            return mapGreaterThanOrEqual(f);
        } else if (filter instanceof IsLessThan f) {
            return mapLessThan(f);
        } else if (filter instanceof IsLessThanOrEqualTo f) {
            return mapLessThanOrEqual(f);
        } else if (filter instanceof IsIn f) {
            return mapIn(f);
        } else if (filter instanceof IsNotIn f) {
            return mapNotIn(f);
        } else if (filter instanceof And f) {
            return mapAnd(f);
        } else if (filter instanceof Or f) {
            return mapOr(f);
        } else if (filter instanceof Not f) {
            return mapNot(f);
        }
        throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
    }

    private String mapEqual(IsEqualTo filter) {
        return metadataKey(filter.key()) + " = " + formatValue(filter.comparisonValue());
    }

    private String mapNotEqual(IsNotEqualTo filter) {
        return metadataKey(filter.key()) + " != " + formatValue(filter.comparisonValue());
    }

    private String mapGreaterThan(IsGreaterThan filter) {
        return metadataKey(filter.key()) + " > " + formatValue(filter.comparisonValue());
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        return metadataKey(filter.key()) + " >= " + formatValue(filter.comparisonValue());
    }

    private String mapLessThan(IsLessThan filter) {
        return metadataKey(filter.key()) + " < " + formatValue(filter.comparisonValue());
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        return metadataKey(filter.key()) + " <= " + formatValue(filter.comparisonValue());
    }

    private String mapIn(IsIn filter) {
        return metadataKey(filter.key()) + " IN (" + formatCollection(filter.comparisonValues()) + ")";
    }

    private String mapNotIn(IsNotIn filter) {
        return metadataKey(filter.key()) + " NOT IN (" + formatCollection(filter.comparisonValues()) + ")";
    }

    private String mapAnd(And filter) {
        return "(" + mapFilter(filter.left()) + " AND " + mapFilter(filter.right()) + ")";
    }

    private String mapOr(Or filter) {
        return "(" + mapFilter(filter.left()) + " OR " + mapFilter(filter.right()) + ")";
    }

    private String mapNot(Not filter) {
        return "NOT (" + mapFilter(filter.expression()) + ")";
    }

    private String metadataKey(String key) {
        return "metadata." + key;
    }

    static String formatValue(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        return "'" + escapeString(value.toString()) + "'";
    }

    static String formatCollection(Collection<?> values) {
        return values.stream()
                .map(LanceDbFilterMapper::formatValue)
                .collect(Collectors.joining(", "));
    }

    private static String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}

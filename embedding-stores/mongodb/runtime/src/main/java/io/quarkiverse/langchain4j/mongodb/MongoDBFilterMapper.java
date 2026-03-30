package io.quarkiverse.langchain4j.mongodb;

import java.util.Collection;

import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

/**
 * Maps {@link Filter} instances to MongoDB {@link Bson} filter expressions.
 */
public class MongoDBFilterMapper {

    private final String metadataFieldName;

    public MongoDBFilterMapper(String metadataFieldName) {
        this.metadataFieldName = metadataFieldName;
    }

    public Bson map(Filter filter) {
        if (filter == null) {
            return null;
        }
        return map0(filter);
    }

    private Bson map0(Filter filter) {
        if (filter instanceof IsEqualTo isEqualToFilter) {
            return mapEqual(isEqualToFilter);
        } else if (filter instanceof IsNotEqualTo isNotEqualToFilter) {
            return mapNotEqual(isNotEqualToFilter);
        } else if (filter instanceof IsGreaterThan isGreaterThanFilter) {
            return mapGreaterThan(isGreaterThanFilter);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualToFilter) {
            return mapGreaterThanOrEqual(isGreaterThanOrEqualToFilter);
        } else if (filter instanceof IsLessThan isLessThanFilter) {
            return mapLessThan(isLessThanFilter);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualToFilter) {
            return mapLessThanOrEqual(isLessThanOrEqualToFilter);
        } else if (filter instanceof IsIn isInFilter) {
            return mapIn(isInFilter);
        } else if (filter instanceof IsNotIn isNotInFilter) {
            return mapNotIn(isNotInFilter);
        } else if (filter instanceof ContainsString containsStringFilter) {
            return mapContainsString(containsStringFilter);
        } else if (filter instanceof And andFilter) {
            return mapAnd(andFilter);
        } else if (filter instanceof Not notFilter) {
            return mapNot(notFilter);
        } else if (filter instanceof Or orFilter) {
            return mapOr(orFilter);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String getFieldPath(String key) {
        return metadataFieldName + "." + key;
    }

    private Bson mapEqual(IsEqualTo filter) {
        return Filters.eq(getFieldPath(filter.key()), filter.comparisonValue());
    }

    private Bson mapNotEqual(IsNotEqualTo filter) {
        return Filters.ne(getFieldPath(filter.key()), filter.comparisonValue());
    }

    private Bson mapGreaterThan(IsGreaterThan filter) {
        return Filters.gt(getFieldPath(filter.key()), filter.comparisonValue());
    }

    private Bson mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        return Filters.gte(getFieldPath(filter.key()), filter.comparisonValue());
    }

    private Bson mapLessThan(IsLessThan filter) {
        return Filters.lt(getFieldPath(filter.key()), filter.comparisonValue());
    }

    private Bson mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        return Filters.lte(getFieldPath(filter.key()), filter.comparisonValue());
    }

    private Bson mapIn(IsIn filter) {
        return Filters.in(getFieldPath(filter.key()), (Collection<?>) filter.comparisonValues());
    }

    private Bson mapNotIn(IsNotIn filter) {
        return Filters.nin(getFieldPath(filter.key()), (Collection<?>) filter.comparisonValues());
    }

    private Bson mapContainsString(ContainsString filter) {
        // MongoDB regex for contains: use Pattern.quote to escape special regex characters
        String pattern = ".*" + java.util.regex.Pattern.quote(filter.comparisonValue()) + ".*";
        return Filters.regex(getFieldPath(filter.key()), pattern, "i");
    }

    private Bson mapAnd(And filter) {
        return Filters.and(map0(filter.left()), map0(filter.right()));
    }

    private Bson mapOr(Or filter) {
        return Filters.or(map0(filter.left()), map0(filter.right()));
    }

    private Bson mapNot(Not filter) {
        return Filters.not(map0(filter.expression()));
    }
}

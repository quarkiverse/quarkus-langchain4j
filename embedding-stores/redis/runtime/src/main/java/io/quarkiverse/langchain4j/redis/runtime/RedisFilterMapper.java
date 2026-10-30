package io.quarkiverse.langchain4j.redis.runtime;

import java.util.regex.Pattern;

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
 * Maps {@link dev.langchain4j.store.embedding.filter.Filter} instances to predicates for Redis queries.
 */
public class RedisFilterMapper {

    private static final Pattern UNSUPPORTED_CHARACTERS = Pattern.compile("[\\p{Cntrl}\\p{Cs}]");

    private static final Pattern SPECIAL_CHARACTERS = Pattern.compile("[\\p{Punct}\\s&&[^_]]");

    public String map(Filter filter) {
        if (filter == null) {
            return "*";
        } else {
            return "(" + map0(filter) + ")";
        }
    }

    private String map0(Filter filter) {
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

    private String mapEqual(IsEqualTo op) {
        if (!(op.comparisonValue() instanceof Number)) {
            throw new UnsupportedOperationException(
                    "Redis embedding store currently only supports filtering based on numeric fields.");
        }
        return field(op.key()) + ":[" + op.comparisonValue() + " " + op.comparisonValue() + "]";
    }

    private String mapNotEqual(IsNotEqualTo op) {
        if (!(op.comparisonValue() instanceof Number)) {
            throw new UnsupportedOperationException(
                    "Redis embedding store currently only supports filtering based on numeric fields.");
        }
        return "-" + field(op.key()) + ":[" + op.comparisonValue() + " " + op.comparisonValue() + "]";
    }

    private String mapGreaterThan(IsGreaterThan op) {
        if (!(op.comparisonValue() instanceof Number)) {
            throw new UnsupportedOperationException(
                    "Redis embedding store currently only supports filtering based on numeric fields.");
        }
        return field(op.key()) + ":[(" + op.comparisonValue() + " inf]";
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo op) {
        if (!(op.comparisonValue() instanceof Number)) {
            throw new UnsupportedOperationException(
                    "Redis embedding store currently only supports filtering based on numeric fields.");
        }
        return field(op.key()) + ":[" + op.comparisonValue() + " inf]";
    }

    private String mapLessThan(IsLessThan op) {
        if (!(op.comparisonValue() instanceof Number)) {
            throw new UnsupportedOperationException(
                    "Redis embedding store currently only supports filtering based on numeric fields.");
        }
        return field(op.key()) + ":[-inf (" + op.comparisonValue() + "]";
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo op) {
        if (!(op.comparisonValue() instanceof Number)) {
            throw new UnsupportedOperationException(
                    "Redis embedding store currently only supports filtering based on numeric fields.");
        }
        return field(op.key()) + ":[-inf " + op.comparisonValue() + "]";
    }

    private String mapIn(IsIn op) {
        throw new UnsupportedOperationException();
    }

    private String mapNotIn(IsNotIn op) {
        throw new UnsupportedOperationException();
    }

    private String mapAnd(And op) {
        return map0(op.left()) + " " + map0(op.right());
    }

    private String mapNot(Not op) {
        throw new UnsupportedOperationException();
    }

    private String mapOr(Or op) {
        throw new UnsupportedOperationException();
    }

    /**
     * Renders a metadata key as a RediSearch field reference. Punctuation and whitespace are escaped, so the key always
     * denotes a single field and can never contribute query syntax of its own. Control characters and unpaired
     * surrogates (which reach Redis as '?') cannot be escaped, hence keys containing them are rejected.
     */
    private static String field(String key) {
        if (UNSUPPORTED_CHARACTERS.matcher(key).find()) {
            throw new IllegalArgumentException(
                    "Redis embedding store cannot filter on metadata keys with control characters or unpaired surrogates.");
        }
        return "@" + SPECIAL_CHARACTERS.matcher(key).replaceAll("\\\\$0");
    }

}

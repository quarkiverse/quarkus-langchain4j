package io.quarkiverse.langchain4j.cost;

import java.lang.reflect.ParameterizedType;

import dev.langchain4j.model.output.TokenUsage;
import io.smallrye.common.annotation.Experimental;

/**
 * Base {@link CacheTokenUsageExtractor} that supports a single provider {@link TokenUsage} subtype, inferred from the
 * type argument. Subclasses only implement {@link #extract(TokenUsage)}.
 */
@Experimental("This feature is experimental and the API is subject to change")
public abstract class AbstractTypeSupportingCacheTokenUsageExtractor<T extends TokenUsage>
        implements CacheTokenUsageExtractor {

    private final Class<?> typeArg;

    protected AbstractTypeSupportingCacheTokenUsageExtractor() {
        this.typeArg = (Class<?>) ((ParameterizedType) getClass().getGenericSuperclass())
                .getActualTypeArguments()[0];
    }

    @Override
    public final boolean supports(TokenUsage tokenUsage) {
        return typeArg.isInstance(tokenUsage);
    }
}

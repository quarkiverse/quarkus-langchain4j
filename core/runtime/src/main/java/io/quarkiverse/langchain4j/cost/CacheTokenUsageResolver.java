package io.quarkiverse.langchain4j.cost;

import java.util.Collections;
import java.util.List;

import jakarta.inject.Singleton;

import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.arc.All;
import io.smallrye.common.annotation.Experimental;

/**
 * Resolves a provider-neutral {@link CacheTokenUsage} from a {@link TokenUsage} by consulting the registered
 * {@link CacheTokenUsageExtractor} beans, or {@link CacheTokenUsage#none()} when none applies.
 */
@Singleton
@Experimental("This feature is experimental and the API is subject to change")
public class CacheTokenUsageResolver {

    private final List<CacheTokenUsageExtractor> extractors;

    public CacheTokenUsageResolver(@All List<CacheTokenUsageExtractor> extractors) {
        this.extractors = extractors != null ? Collections.unmodifiableList(extractors) : Collections.emptyList();
    }

    public CacheTokenUsage resolve(TokenUsage tokenUsage) {
        if (tokenUsage != null) {
            for (CacheTokenUsageExtractor extractor : extractors) {
                if (extractor.supports(tokenUsage)) {
                    return extractor.extract(tokenUsage);
                }
            }
        }
        return CacheTokenUsage.none();
    }
}

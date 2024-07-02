package io.quarkiverse.langchain4j.runtime;

import java.time.Duration;
import java.util.function.Function;

import io.quarkiverse.langchain4j.runtime.cache.AiCache;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheConfig;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheProvider;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheStore;
import io.quarkiverse.langchain4j.runtime.cache.MessageWindowAiCache;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AiCacheRecorder {

    public Function<SyntheticCreationalContext<AiCacheProvider>, AiCacheProvider> messageWindow(AiCacheConfig config) {
        return new Function<>() {
            @Override
            public AiCacheProvider apply(SyntheticCreationalContext<AiCacheProvider> context) {

                AiCacheStore aiCacheStore = context.getInjectedReference(AiCacheStore.class);
                double threshold = config.threshold();
                int maxSize = config.maxSize();
                Duration ttl = config.ttl().orElse(null);

                return new AiCacheProvider() {
                    @Override
                    public AiCache get(Object memoryId) {
                        return MessageWindowAiCache.Builder
                                .create(memoryId)
                                .ttl(ttl)
                                .maxSize(maxSize)
                                .threshold(threshold)
                                .store(aiCacheStore)
                                .build();
                    }
                };
            }
        };
    }
}

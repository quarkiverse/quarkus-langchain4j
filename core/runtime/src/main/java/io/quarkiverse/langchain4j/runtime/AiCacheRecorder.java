package io.quarkiverse.langchain4j.runtime;

import java.time.Duration;
import java.util.function.Function;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.cache.AiCache;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheProvider;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheStore;
import io.quarkiverse.langchain4j.runtime.cache.FixedAiCache;
import io.quarkiverse.langchain4j.runtime.cache.config.AiCacheConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AiCacheRecorder {

    public Function<SyntheticCreationalContext<AiCacheProvider>, AiCacheProvider> messageWindow(AiCacheConfig config,
            String embeddingModelName) {
        return new Function<>() {
            @Override
            public AiCacheProvider apply(SyntheticCreationalContext<AiCacheProvider> context) {

                EmbeddingModel embeddingModel;
                AiCacheStore aiCacheStore = context.getInjectedReference(AiCacheStore.class);

                if (NamedConfigUtil.isDefault(embeddingModelName)) {
                    embeddingModel = context.getInjectedReference(EmbeddingModel.class);
                } else {
                    embeddingModel = context.getInjectedReference(EmbeddingModel.class,
                            ModelName.Literal.of(embeddingModelName));
                }

                double threshold = config.threshold();
                int maxSize = config.maxSize();
                Duration ttl = config.ttl().orElse(null);
                String queryPrefix = config.embedding().queryPrefix().orElse("");
                String passagePrefix = config.embedding().passagePrefix().orElse("");

                return new AiCacheProvider() {
                    @Override
                    public AiCache get(Object memoryId) {
                        return FixedAiCache.Builder
                                .create(memoryId)
                                .ttl(ttl)
                                .maxSize(maxSize)
                                .threshold(threshold)
                                .queryPrefix(queryPrefix)
                                .passagePrefix(passagePrefix)
                                .embeddingModel(embeddingModel)
                                .store(aiCacheStore)
                                .build();
                    }
                };
            }
        };
    }
}

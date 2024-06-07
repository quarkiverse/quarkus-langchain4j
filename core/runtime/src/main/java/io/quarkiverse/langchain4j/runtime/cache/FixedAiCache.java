package io.quarkiverse.langchain4j.runtime.cache;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheStore.CacheRecord;

/**
 * This {@link AiCache} default implementation.
 */
public class FixedAiCache implements AiCache {

    private final Object id;
    private final Integer maxMessages;
    private final AiCacheStore store;
    private final Double threshold;
    private final Duration ttl;
    private final String queryPrefix;
    private final String passagePrefix;
    private final EmbeddingModel embeddingModel;
    private final ReentrantLock lock;

    public FixedAiCache(Builder builder) {
        this.id = builder.id;
        this.maxMessages = builder.maxSize;
        this.store = builder.store;
        this.ttl = builder.ttl;
        this.threshold = builder.threshold;
        this.queryPrefix = builder.queryPrefix;
        this.passagePrefix = builder.passagePrefix;
        this.embeddingModel = builder.embeddingModel;
        this.lock = new ReentrantLock();
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(SystemMessage systemMessage, UserMessage userMessage, AiMessage aiResponse) {

        if (Objects.isNull(userMessage) || Objects.isNull(aiResponse)) {
            return;
        }

        String query;
        if (Objects.isNull(systemMessage) || Objects.isNull(systemMessage.text()) || systemMessage.text().isBlank())
            query = userMessage.text();
        else
            query = "%s%s%s".formatted(passagePrefix, systemMessage.text(), userMessage.text());

        try {

            lock.lock();

            List<CacheRecord> elements = store.getAll(id)
                    .stream()
                    .filter(this::checkTTL)
                    .collect(Collectors.toList());

            if (elements.size() == maxMessages) {
                return;
            }

            elements.add(CacheRecord.of(embeddingModel.embed(query).content(), aiResponse));
            store.updateCache(id, elements);

        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<AiMessage> search(SystemMessage systemMessage, UserMessage userMessage) {

        if (Objects.isNull(userMessage))
            return Optional.empty();

        String query;
        if (Objects.isNull(systemMessage) || Objects.isNull(systemMessage.text()) || systemMessage.text().isBlank())
            query = userMessage.text();
        else
            query = "%s%s%s".formatted(queryPrefix, systemMessage.text(), userMessage.text());

        try {

            lock.lock();

            double maxScore = 0;
            AiMessage result = null;
            List<CacheRecord> records = store.getAll(id)
                    .stream()
                    .filter(this::checkTTL)
                    .collect(Collectors.toList());

            for (var record : records) {

                var relevanceScore = CosineSimilarity.between(embeddingModel.embed(query).content(), record.embedded());
                var score = (float) CosineSimilarity.fromRelevanceScore(relevanceScore);

                if (score >= threshold.doubleValue() && score >= maxScore) {
                    maxScore = score;
                    result = record.response();
                }
            }

            store.updateCache(id, records);
            return Optional.ofNullable(result);

        } finally {
            lock.unlock();
        }
    }

    private boolean checkTTL(CacheRecord record) {

        if (ttl == null)
            return true;

        var expiredTime = Date.from(record.creation().plus(ttl));
        var currentTime = new Date();

        if (currentTime.after(expiredTime)) {
            return false;
        }

        return true;
    }

    @Override
    public void clear() {
        store.deleteCache(id);
    }

    public static class Builder {

        Object id;
        Integer maxSize;
        AiCacheStore store;
        Double threshold;
        Duration ttl;
        String queryPrefix;
        String passagePrefix;
        EmbeddingModel embeddingModel;

        private Builder(Object id) {
            this.id = id;
        }

        public static Builder create(Object id) {
            return new Builder(id);
        }

        public Builder maxSize(Integer maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder store(AiCacheStore store) {
            this.store = store;
            return this;
        }

        public Builder threshold(Double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder queryPrefix(String queryPrefix) {
            this.queryPrefix = queryPrefix;
            return this;
        }

        public Builder passagePrefix(String passagePrefix) {
            this.passagePrefix = passagePrefix;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public AiCache build() {
            return new FixedAiCache(this);
        }
    }
}

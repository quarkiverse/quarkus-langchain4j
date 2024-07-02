package io.quarkiverse.langchain4j.runtime.cache;

import java.time.Duration;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.store.embedding.CosineSimilarity;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheStore.CacheRecord;

/**
 * This {@link AiCache} implementation operates as a sliding window of messages.
 */
public class MessageWindowAiCache implements AiCache {

    private final Object id;
    private final Integer maxMessages;
    private final AiCacheStore store;
    private final Double threshold;
    private final Duration ttl;
    private final ReentrantLock lock;

    public MessageWindowAiCache(Builder builder) {
        this.id = builder.id;
        this.maxMessages = builder.maxSize;
        this.store = builder.store;
        this.ttl = builder.ttl;
        this.threshold = builder.threshold;
        this.lock = new ReentrantLock();
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(Embedding query, AiMessage response) {

        if (Objects.isNull(query) || Objects.isNull(response)) {
            return;
        }

        try {

            lock.lock();

            List<CacheRecord> elements = store.getAll(id);
            if (elements.size() == maxMessages) {
                elements.remove(0);
            }

            List<CacheRecord> update = new LinkedList<>();
            for (int i = 0; i < elements.size(); i++) {

                var expiredTime = Date.from(elements.get(i).creation().plus(ttl));
                var currentTime = new Date();

                if (currentTime.after(expiredTime))
                    continue;

                update.add(elements.get(i));
            }

            update.add(CacheRecord.of(query, response));
            store.updateCache(id, update);

        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<AiMessage> search(Embedding query) {

        if (Objects.isNull(query))
            return Optional.empty();

        var elements = store.getAll(id);
        double maxScore = 0;
        AiMessage result = null;

        for (var cacheRecord : elements) {

            if (ttl != null) {
                var expiredTime = Date.from(cacheRecord.creation().plus(ttl));
                var currentTime = new Date();

                if (currentTime.after(expiredTime))
                    continue;
            }

            var relevanceScore = CosineSimilarity.between(query, cacheRecord.embedded());
            var score = (float) CosineSimilarity.fromRelevanceScore(relevanceScore);

            if (score >= threshold.doubleValue() && score >= maxScore) {
                maxScore = score;
                result = cacheRecord.response();
            }
        }

        return Optional.ofNullable(result);
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

        public AiCache build() {
            return new MessageWindowAiCache(this);
        }
    }
}

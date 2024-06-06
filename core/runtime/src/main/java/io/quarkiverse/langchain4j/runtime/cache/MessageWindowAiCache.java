package io.quarkiverse.langchain4j.runtime.cache;

import java.time.Duration;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
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
    private final String queryPrefix;
    private final String passagePrefix;
    private final EmbeddingModel embeddingModel;
    private final ReentrantLock lock;

    public MessageWindowAiCache(Builder builder) {
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

            List<CacheRecord> elements = store.getAll(id);
            if (elements.size() == maxMessages) {
                elements.remove(0);
            }

            List<CacheRecord> items = new LinkedList<>();
            for (int i = 0; i < elements.size(); i++) {

                var expiredTime = Date.from(elements.get(i).creation().plus(ttl));
                var currentTime = new Date();

                if (currentTime.after(expiredTime))
                    continue;

                items.add(elements.get(i));
            }

            items.add(CacheRecord.of(embeddingModel.embed(query).content(), aiResponse));
            store.updateCache(id, items);

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

            var relevanceScore = CosineSimilarity.between(embeddingModel.embed(query).content(), cacheRecord.embedded());
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
            return new MessageWindowAiCache(this);
        }
    }
}

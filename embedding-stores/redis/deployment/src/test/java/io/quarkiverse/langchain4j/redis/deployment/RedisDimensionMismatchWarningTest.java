package io.quarkiverse.langchain4j.redis.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class RedisDimensionMismatchWarningTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.redis.dimension", "384");

    @Inject
    RedisEmbeddingStore embeddingStore;

    @Test
    void shouldWarnOnceWhenEmbeddingDimensionDoesNotMatch() {
        LogCapturingHandler handler = new LogCapturingHandler();
        Logger.getLogger("").addHandler(handler);
        try {
            addIgnoringFailure(512);
            addIgnoringFailure(512);
        } finally {
            Logger.getLogger("").removeHandler(handler);
        }

        List<String> messages = handler.messagesContaining("does not match the configured dimension");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains("(512)").contains("(384)")
                .contains("quarkus.langchain4j.redis.dimension");
    }

    private void addIgnoringFailure(int dimension) {
        try {
            embeddingStore.add(Embedding.from(new float[dimension]));
        } catch (RuntimeException ignored) {
            // the index was created with a fixed vector dimension, the insert may be rejected by the server
        }
    }

    private static final class LogCapturingHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        synchronized List<String> messagesContaining(String text) {
            return records.stream()
                    .filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
                    .map(LogRecord::getMessage)
                    .filter(message -> message != null && message.contains(text))
                    .toList();
        }
    }
}

package io.quarkiverse.langchain4j.pinecone.test;

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
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class PineconeDimensionMismatchWarningTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.api-key", "test-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.environment", "gcp-starter")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.project-id", "proj123")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.index-name", "default-index")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.dimension", "1536");

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Test
    void shouldWarnOnceWhenEmbeddingDimensionDoesNotMatch() {
        LogCapturingHandler handler = new LogCapturingHandler();
        Logger.getLogger("").addHandler(handler);
        try {
            addIgnoringFailure(3);
            addIgnoringFailure(3);
        } finally {
            Logger.getLogger("").removeHandler(handler);
        }

        List<String> messages = handler.messagesContaining("does not match the configured dimension");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains("(3)").contains("(1536)")
                .contains("quarkus.langchain4j.pinecone.dimension");
    }

    private void addIgnoringFailure(int dimension) {
        try {
            embeddingStore.add(Embedding.from(new float[dimension]));
        } catch (RuntimeException ignored) {
            // no Pinecone backend is available in tests, the REST call fails after the warning was emitted
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

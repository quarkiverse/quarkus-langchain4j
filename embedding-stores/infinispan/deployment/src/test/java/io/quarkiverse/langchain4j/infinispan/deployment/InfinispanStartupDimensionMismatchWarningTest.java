package io.quarkiverse.langchain4j.infinispan.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies the startup warning emitted by the core dimension validation recorder when the dimension of the
 * embedding model does not match the configured dimension of a registered embedding store.
 */
public class InfinispanStartupDimensionMismatchWarningTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(FakeEmbeddingModel.class))
            .overrideConfigKey("quarkus.arc.remove-unused-beans", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.infinispan.dimension", "384")
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(records -> {
                List<String> warnings = records.stream()
                        .map(LogRecord::getMessage)
                        .filter(message -> message != null && message.contains("configured dimension"))
                        .toList();
                assertThat(warnings).hasSize(1);
                assertThat(warnings.get(0)).contains("(512)").contains("(384)")
                        .contains("quarkus.langchain4j.infinispan.dimension");
            });

    @Test
    void shouldNotNeedAnyTestBody() {
        // startup warnings are asserted by the QuarkusUnitTest extension in afterAll
    }

    @ApplicationScoped
    public static final class FakeEmbeddingModel implements EmbeddingModel {

        @Override
        public int dimension() {
            return 512;
        }
    }
}

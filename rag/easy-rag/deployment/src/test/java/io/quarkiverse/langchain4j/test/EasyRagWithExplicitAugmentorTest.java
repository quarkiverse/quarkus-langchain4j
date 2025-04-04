package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import io.quarkus.test.QuarkusUnitTest;

public class EasyRagWithExplicitAugmentorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.easy-rag.path=src/test/resources/ragdocuments"),
                            "application.properties"))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(EasyRagWithExplicitAugmentorTest::verifyLogRecords);

    @ApplicationScoped
    public static class ExplicitRetrievalAugmentor implements RetrievalAugmentor {
        @Override
        public AugmentationResult augment(AugmentationRequest augmentationRequest) {
            return null;
        }
    }

    @Inject
    RetrievalAugmentor retrievalAugmentor;

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream().map(LogRecord::getMessage))
                .contains(
                        "Ingesting documents from path: src/test/resources/ragdocuments, path matcher = glob:**, recursive = true")
                .contains("Ingested 2 files as 2 documents")
                .doesNotContain("Writing embeddings to %s")
                .doesNotContain("Reading embeddings from %s");
    }

    @Test
    public void verifyThatExplicitRetrievalAugmentorHasPriority() {
        ;
        assertInstanceOf(ExplicitRetrievalAugmentor.class, retrievalAugmentor);
    }

}

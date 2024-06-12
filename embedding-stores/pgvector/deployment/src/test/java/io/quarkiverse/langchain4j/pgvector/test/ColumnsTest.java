package io.quarkiverse.langchain4j.pgvector.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class ColumnsTest extends LangChain4jPgVectorBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.pgvector.dimension=384\n" +
                                    "quarkus.langchain4j.pgvector.drop-table-first=true\n" +
                                    "quarkus.class-loading.parent-first-artifacts=ai.djl.huggingface:tokenizers\n" +
                                    "quarkus.log.category.\"io.quarkiverse.langchain4j.pgvector\".level=DEBUG\n\n" +
                                    "quarkus.langchain4j.pgvector.metadata.storage-mode=COLUMN_PER_KEY\n" +
                                    "quarkus.langchain4j.pgvector.metadata.column-definitions=key text NULL, name text NULL, " +
                                    "age float NULL, city varchar null, country varchar null\n" +
                                    "quarkus.langchain4j.pgvector.metadata.indexes=key, name, age"),
                            "application.properties"));

    @Test
    // do not test parent method to avoid defining all the metadata fields
    void should_add_embedding_with_segment_with_metadata() {
    }

}

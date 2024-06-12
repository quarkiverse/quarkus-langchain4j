package io.quarkiverse.langchain4j.pgvector.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class JSONBMultiIndexTest extends LangChain4jPgVectorBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.pgvector.dimension=384\n" +
                                    "quarkus.langchain4j.pgvector.drop-table-first=true\n" +
                                    "quarkus.class-loading.parent-first-artifacts=ai.djl.huggingface:tokenizers\n" +
                                    "quarkus.log.category.\"io.quarkiverse.langchain4j.pgvector\".level=DEBUG\n\n" +
                                    "quarkus.langchain4j.pgvector.metadata.storage-mode=COMBINED_JSONB\n" +
                                    "quarkus.langchain4j.pgvector.metadata.column-definitions=metadata_b JSONB NULL\n" +
                                    "quarkus.langchain4j.pgvector.metadata.indexes=(metadata_b->'key'), (metadata_b->'name'), (metadata_b->'age')\n"
                                    +
                                    "quarkus.langchain4j.pgvector.metadata.index-type=GIN"),
                            "application.properties"));

}

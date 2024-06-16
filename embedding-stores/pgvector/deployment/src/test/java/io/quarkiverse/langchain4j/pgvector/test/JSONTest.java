package io.quarkiverse.langchain4j.pgvector.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class JSONTest extends LangChain4jPgVectorBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.pgvector.dimension=384\n" +
                                    "quarkus.langchain4j.pgvector.drop-table-first=true\n" +
                                    "quarkus.class-loading.parent-first-artifacts=ai.djl.huggingface:tokenizers\n" +
                                    "quarkus.log.category.\"io.quarkiverse.langchain4j.pgvector\".level=DEBUG\n\n"),
                            "application.properties"));

    // Default behavior
}

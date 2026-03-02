package io.quarkiverse.langchain4j.hibernate.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class DynamicTest extends LangChain4jPgVectorBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.embeddings-ds.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.datasource.embeddings-ds.db-kind=postgresql\n" +
                                    "quarkus.hibernate-orm.datasource=embeddings-ds\n" +
                                    "quarkus.langchain4j.hibernate-dynamic.datasource=embeddings-ds\n" +
                                    "quarkus.langchain4j.hibernate-dynamic.dimension=384\n" +
                                    "quarkus.langchain4j.hibernate-dynamic.drop-table-first=true\n" +
                                    "quarkus.class-loading.parent-first-artifacts=ai.djl.huggingface:tokenizers\n" +
                                    "quarkus.log.category.\"io.quarkiverse.langchain4j.hibernate\".level=DEBUG\n\n"),
                            "application.properties"));

    // Default behavior
}

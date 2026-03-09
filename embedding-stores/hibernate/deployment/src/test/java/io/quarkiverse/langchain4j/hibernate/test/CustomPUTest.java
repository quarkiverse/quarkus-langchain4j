package io.quarkiverse.langchain4j.hibernate.test;

import org.hibernate.cfg.SchemaToolingSettings;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class CustomPUTest extends LangChain4jPgVectorBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(GenericEmbeddingEntity.class)
                    .addAsResource("setup.sql")
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.datasource.db-kind=postgresql\n" +
                                    "quarkus.hibernate-orm.my-pu.datasource=<default>\n" +
                                    "quarkus.hibernate-orm.my-pu.schema-management.strategy=drop-and-create\n" +
                                    "quarkus.hibernate-orm.my-pu.packages=" + GenericEmbeddingEntity.class.getPackageName()
                                    + "\n" +
                                    "quarkus.hibernate-orm.my-pu.unsupported-properties.\""
                                    + SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE + "\"=script-then-metadata\n" +
                                    "quarkus.hibernate-orm.my-pu.unsupported-properties.\""
                                    + SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE + "\"=setup.sql\n" +
                                    "quarkus.langchain4j.hibernate-orm.persistence-unit=my-pu\n" +
                                    "quarkus.langchain4j.hibernate-orm.entity-name=GenericEmbeddingEntity\n" +
                                    "quarkus.class-loading.parent-first-artifacts=ai.djl.huggingface:tokenizers\n" +
                                    "quarkus.log.category.\"io.quarkiverse.langchain4j.hibernate\".level=DEBUG\n\n"),
                            "application.properties"));

    // Default behavior

    @Test
    @Disabled("The test tries to assign an id explicitly, but the entity uses a generator that doesn't allow assignment")
    @Override
    protected void should_add_embedding_with_id() {
        super.should_add_embedding_with_id();
    }

    @Test
    @Disabled("The test tries to assign an id explicitly, but the entity uses a generator that doesn't allow assignment")
    @Override
    protected void should_add_multiple_embeddings_with_ids_and_segments() {
        super.should_add_multiple_embeddings_with_ids_and_segments();
    }
}

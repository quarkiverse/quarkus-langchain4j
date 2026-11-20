package io.quarkiverse.langchain4j.hibernate.test.multi;

import jakarta.inject.Inject;

import org.hibernate.cfg.SchemaToolingSettings;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusExtensionTest;

public class MultiEntityTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyEmbeddingEntity.class, AnotherEmbeddingEntity.class, BaseTest.class)
                    .addAsResource("setup.sql")
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.datasource.db-kind=postgresql\n" +
                                    "quarkus.hibernate-orm.my-pu.datasource=<default>\n" +
                                    "quarkus.hibernate-orm.my-pu.schema-management.strategy=drop-and-create\n" +
                                    "quarkus.hibernate-orm.my-pu.packages=" + MyEmbeddingEntity.class.getPackageName()
                                    + "\n" +
                                    "quarkus.hibernate-orm.my-pu.unsupported-properties.\""
                                    + SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE + "\"=script-then-metadata\n" +
                                    "quarkus.hibernate-orm.my-pu.unsupported-properties.\""
                                    + SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE + "\"=setup.sql\n" +
                                    "quarkus.class-loading.parent-first-artifacts=ai.djl.huggingface:tokenizers\n" +
                                    "quarkus.log.category.\"io.quarkiverse.langchain4j.hibernate\".level=DEBUG\n\n"),
                            "application.properties"));

    @Inject
    @EmbeddingStoreName("MyEmbeddingEntity")
    protected EmbeddingStore<TextSegment> custom;

    @Inject
    @EmbeddingStoreName("another")
    protected EmbeddingStore<TextSegment> another;

    @Test
    public void test() {
        runtests(custom);
        runtests(another);

    }

    private static void runtests(EmbeddingStore<TextSegment> value) {
        BaseTest custom = new BaseTest(value);
        custom.should_add_embedding();
    }
}

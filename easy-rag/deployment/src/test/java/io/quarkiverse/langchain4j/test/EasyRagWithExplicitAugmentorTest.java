package io.quarkiverse.langchain4j.test;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
import io.quarkus.test.QuarkusUnitTest;

public class EasyRagWithExplicitAugmentorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.easy-rag.path=src/test/resources/ragdocuments"),
                            "application.properties"));

    @ApplicationScoped
    public static class ExplicitRetrievalAugmentor implements RetrievalAugmentor {
        @Override
        public UserMessage augment(UserMessage userMessage, Metadata metadata) {
            return null;
        }
    }

    @Inject
    RetrievalAugmentor retrievalAugmentor;

    @Test
    public void verifyThatExplicitRetrievalAugmentorHasPriority() {
        ;
        assertInstanceOf(ExplicitRetrievalAugmentor.class, retrievalAugmentor);
    }

}

package org.acme.example;

import static org.hamcrest.Matchers.containsString;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

/**
 * Verifies that an application with Easy RAG starts in dev mode, where the build steps of the Dev UI also run, and
 * that it ingests a document and retrieves from it.
 * <p>
 * This module only depends on runtime artifacts, like an application, so Quarkus resolves the deployment ones. That
 * is what lets a deployment dependency built for another Quarkus major, like an old Dev UI SPI, take precedence over
 * the one of the Quarkus version in use: on the test classpath of an extension the order hides the conflict.
 */
public class EasyRagDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(RetrievalResource.class)
                    .addAsResource(new StringAsset("Emma was born in 2001."), "devmode-ragdocuments/emma.txt")
                    .addAsResource(new StringAsset(String.join("\n",
                            "quarkus.langchain4j.easy-rag.path=devmode-ragdocuments",
                            "quarkus.langchain4j.easy-rag.path-type=CLASSPATH")),
                            "application.properties"));

    @Test
    void documentIsIngestedAndRetrieved() {
        RestAssured.given()
                .queryParam("question", "When was Emma born?")
                .get("/retrieval")
                .then()
                .statusCode(200)
                .body(containsString("2001"));
    }

    @Path("/retrieval")
    public static class RetrievalResource {

        @Inject
        EmbeddingModel embeddingModel;

        @Inject
        EmbeddingStore<TextSegment> embeddingStore;

        @GET
        public String retrieve(@QueryParam("question") String question) {
            Embedding embedding = embeddingModel.embed(question).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(1)
                    .build();
            return embeddingStore.search(request).matches().get(0).embedded().text();
        }
    }
}

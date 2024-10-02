package org.acme.test;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;

@Path("/in-process-embedding")
public class InProcessEmbeddingResource {

    @Inject
    AllMiniLmL6V2EmbeddingModel allMiniLmL6V2QuantizedEmbeddingModel;

    @Inject
    EmbeddingModel embeddingModel;

    @POST
    public String computeEmbedding(String sentence) {

        var r1 = allMiniLmL6V2QuantizedEmbeddingModel.embed(sentence);
        var r2 = embeddingModel.embed(sentence);

        return "AllMiniLmL6V2EmbeddingModel: " + r1.content().dimension() + "\n" + "embeddingModel: "
                + r2.content().dimension();
    }

}

package org.acme.test;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.e5smallv2.E5SmallV2EmbeddingModel;

@Path("/in-process-embedding")
public class InProcessEmbeddingResource {

    @Inject
    E5SmallV2EmbeddingModel typedModel;

    @Inject
    EmbeddingModel embeddingModel;

    @POST
    public String computeEmbedding(String sentence) {
        var r1 = typedModel.embed(sentence);
        var r2 = embeddingModel.embed(sentence);

        return "e5SmallV2EmbeddingModel: " + r1.content().dimension() + "\n" + "embeddingModel: "
                + r2.content().dimension();
    }

}

package io.quarkiverse.langchain4j.bam;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.output.Response;

public class BamEmbeddingModel extends BamModel implements EmbeddingModel, TokenCountEstimator {

    public BamEmbeddingModel(Builder config) {
        super(config);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<Embedding> result = new ArrayList<>();
        for (TextSegment textSegment : textSegments) {

            var request = new EmbeddingRequest(modelId, textSegment.text());
            var response = client.embeddings(request, token, version);

            var vector = response.results().get(0);
            result.add(Embedding.from(vector));
        }

        return Response.from(result);
    }

    @Override
    public int estimateTokenCount(String text) {

        var request = new TokenizationRequest(modelId, text);
        return client.tokenization(request, token, version).results().get(0).tokenCount();
    }
}

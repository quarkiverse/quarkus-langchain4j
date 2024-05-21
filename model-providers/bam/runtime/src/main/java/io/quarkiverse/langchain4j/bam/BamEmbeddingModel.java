package io.quarkiverse.langchain4j.bam;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.bam.EmbeddingResponse.Result;

public class BamEmbeddingModel extends BamModel implements EmbeddingModel, TokenCountEstimator {

    public BamEmbeddingModel(Builder config) {
        super(config);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        if (Objects.isNull(textSegments) || textSegments.isEmpty())
            return Response.from(List.of());

        var inputs = textSegments.stream()
                .map(TextSegment::text)
                .collect(Collectors.toList());

        EmbeddingRequest request = new EmbeddingRequest(modelId, inputs);
        var result = client.embeddings(request, token, version);

        return Response.from(
                result.results()
                        .stream()
                        .map(Result::embedding)
                        .map(Embedding::from)
                        .collect(Collectors.toList()));
    }

    @Override
    public int estimateTokenCount(String text) {

        var request = new TokenizationRequest(modelId, text);
        return client.tokenization(request, token, version).results().get(0).tokenCount();
    }
}

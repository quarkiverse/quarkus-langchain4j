package io.quarkiverse.langchain4j.watsonx;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingRequest;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingResponse;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingResponse.Result;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;

public class WatsonxEmbeddingModel extends WatsonxModel implements EmbeddingModel, TokenCountEstimator {

    public WatsonxEmbeddingModel(Builder config) {
        super(config);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        if (Objects.isNull(textSegments) || textSegments.isEmpty())
            return Response.from(List.of());

        var inputs = textSegments.stream()
                .map(TextSegment::text)
                .collect(Collectors.toList());

        EmbeddingRequest request = new EmbeddingRequest(modelId, projectId, inputs);
        EmbeddingResponse result = retryOn(new Callable<EmbeddingResponse>() {
            @Override
            public EmbeddingResponse call() throws Exception {
                return client.embeddings(request, version);
            }
        });

        return Response.from(
                result.results()
                        .stream()
                        .map(Result::embedding)
                        .map(Embedding::from)
                        .collect(Collectors.toList()));
    }

    @Override
    public int estimateTokenCount(String text) {

        if (Objects.isNull(text) || text.isEmpty())
            return 0;

        var request = new TokenizationRequest(modelId, text, projectId);
        return retryOn(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return client.tokenization(request, version).result().tokenCount();
            }
        });
    }
}

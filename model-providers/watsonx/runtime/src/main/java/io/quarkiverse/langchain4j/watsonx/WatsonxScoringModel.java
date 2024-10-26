package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.watsonx.bean.ScoringParameters;
import io.quarkiverse.langchain4j.watsonx.bean.ScoringRequest;
import io.quarkiverse.langchain4j.watsonx.bean.ScoringResponse;
import io.quarkiverse.langchain4j.watsonx.bean.ScoringResponse.ScoringOutput;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonxException;

public class WatsonxScoringModel extends Watsonx implements ScoringModel {

    private final ScoringParameters parameters;

    public WatsonxScoringModel(Builder builder) {
        super(builder);

        if (builder.truncateInputTokens != null)
            this.parameters = new ScoringParameters(builder.truncateInputTokens);
        else
            this.parameters = null;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> textSegments, String query) {

        if (Objects.isNull(query) || query.isEmpty())
            throw new WatsonxException("The field \"query\" can not be null or empty", 400);

        if (Objects.isNull(textSegments) || textSegments.isEmpty())
            return Response.from(List.of());

        ScoringRequest request = ScoringRequest.of(modelId, spaceId, projectId, query, textSegments, parameters);
        ScoringResponse response = retryOn(new Callable<ScoringResponse>() {
            @Override
            public ScoringResponse call() throws Exception {
                return client.rerank(request, version);
            }
        });

        var content = new Double[response.results().size()];
        // From the ScoringModel interface documentation: The order of scores corresponds to the order of TextSegments.
        for (ScoringOutput rerankScore : response.results()) {
            content[rerankScore.index()] = rerankScore.score();
        }

        return Response.from(Arrays.asList(content), new TokenUsage(response.inputTokenCount()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends Watsonx.Builder<Builder> {

        private Integer truncateInputTokens;

        public Builder truncateInputTokens(Integer truncateInputTokens) {
            this.truncateInputTokens = truncateInputTokens;
            return this;
        }

        public WatsonxScoringModel build() {
            return new WatsonxScoringModel(this);
        }
    }
}

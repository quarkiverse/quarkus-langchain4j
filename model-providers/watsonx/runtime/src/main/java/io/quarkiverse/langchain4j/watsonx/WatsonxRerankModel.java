package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;

import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.watsonx.bean.TextRerankParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextRerankRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextRerankResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextRerankResponse.TextRerankOutput;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonxException;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class WatsonxRerankModel implements ScoringModel {

    private final String modelId, projectId, version;
    private final TextRerankParameters parameters;
    private final WatsonxRestApi client;

    public WatsonxRerankModel(Builder builder) {

        QuarkusRestClientBuilder restClientBuilder = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(builder.url)
                .clientHeadersFactory(new BearerTokenHeaderFactory(builder.tokenGenerator))
                .connectTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS);

        if (builder.logRequests || builder.logResponses) {
            restClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            restClientBuilder.clientLogger(new WatsonxRestApi.WatsonClientLogger(
                    builder.logRequests,
                    builder.logResponses));
        }

        this.client = restClientBuilder.build(WatsonxRestApi.class);
        this.modelId = builder.modelId;
        this.projectId = builder.projectId;
        this.version = builder.version;

        if (builder.truncateInputTokens != null)
            this.parameters = new TextRerankParameters(builder.truncateInputTokens);
        else
            this.parameters = null;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> textSegments, String query) {

        if (Objects.isNull(query) || query.isEmpty())
            throw new WatsonxException("The field \"query\" can not be null or empty", 400);

        if (Objects.isNull(textSegments) || textSegments.isEmpty())
            return Response.from(List.of());

        TextRerankRequest request = TextRerankRequest.of(modelId, projectId, query, textSegments, parameters);
        TextRerankResponse response = retryOn(new Callable<TextRerankResponse>() {
            @Override
            public TextRerankResponse call() throws Exception {
                return client.rerank(request, version);
            }
        });

        var content = new Double[response.results().size()];
        // From the ScoringModel interface documentation: The order of scores corresponds to the order of TextSegments.
        for (TextRerankOutput rerankScore : response.results()) {
            content[rerankScore.index()] = rerankScore.score();
        }

        return Response.from(Arrays.asList(content), new TokenUsage(response.inputTokenCount()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String modelId;
        private String version;
        private String projectId;
        private Duration timeout;
        private URL url;
        private Integer truncateInputTokens;
        public boolean logResponses;
        public boolean logRequests;
        private WatsonxTokenGenerator tokenGenerator;

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder tokenGenerator(WatsonxTokenGenerator tokenGenerator) {
            this.tokenGenerator = tokenGenerator;
            return this;
        }

        public Builder truncateInputTokens(Integer truncateInputTokens) {
            this.truncateInputTokens = truncateInputTokens;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public WatsonxRerankModel build() {
            return new WatsonxRerankModel(this);
        }
    }
}

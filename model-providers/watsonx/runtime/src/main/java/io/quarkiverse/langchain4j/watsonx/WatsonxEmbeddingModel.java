package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingRequest;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingResponse;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingResponse.Result;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.client.filter.BearerTokenHeaderFactory;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class WatsonxEmbeddingModel implements EmbeddingModel, TokenCountEstimator {

    private final String modelId, projectId, version;
    private final WatsonxRestApi client;

    public WatsonxEmbeddingModel(Builder builder) {

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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String modelId;
        private String version;
        private String projectId;
        private Duration timeout;
        private boolean logResponses;
        private boolean logRequests;
        private URL url;
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

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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

        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        public Builder tokenGenerator(WatsonxTokenGenerator tokenGenerator) {
            this.tokenGenerator = tokenGenerator;
            return this;
        }

        public WatsonxEmbeddingModel build() {
            return new WatsonxEmbeddingModel(this);
        }
    }
}

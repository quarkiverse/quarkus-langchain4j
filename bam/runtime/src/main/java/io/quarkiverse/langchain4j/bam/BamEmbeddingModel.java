package io.quarkiverse.langchain4j.bam;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class BamEmbeddingModel implements EmbeddingModel, TokenCountEstimator {

    private final String token;
    private final String modelId;
    private final String version;
    public boolean logResponses;
    public boolean logRequests;
    private final BamRestApi client;

    public BamEmbeddingModel(Builder config) {

        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(config.url)
                .connectTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.timeout.toSeconds(), TimeUnit.SECONDS);

        if (config.logRequests || config.logResponses) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new BamRestApi.WatsonClientLogger(
                    config.logRequests,
                    config.logResponses));
        }

        this.client = builder.build(BamRestApi.class);
        this.token = config.accessToken;
        this.modelId = config.modelId;
        this.version = config.version;
    }

    public static Builder builder() {
        return new Builder();
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
        return client.tokenization(request, token, version).tokenCount();
    }

    public static final class Builder {

        private String accessToken;
        private String version;
        private URI url = URI.create("https://bam-api.res.ibm.com");
        private Duration timeout = Duration.ofSeconds(15);
        private String modelId;
        public boolean logResponses;
        public boolean logRequests;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder url(URL url) {
            try {
                this.url = url.toURI();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
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

        public BamEmbeddingModel build() {
            return new BamEmbeddingModel(this);
        }
    }
}

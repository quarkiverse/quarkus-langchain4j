package io.quarkiverse.langchain4j.huggingface;

import static dev.langchain4j.model.huggingface.HuggingFaceModelName.SENTENCE_TRANSFORMERS_ALL_MINI_LM_L6_V2;
import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import dev.langchain4j.model.output.Response;

/**
 * This is a Quarkus specific version of the HuggingFace model.
 * <p>
 * TODO: remove this in the future when the stock {@link dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel}
 * has been updated to fit our needs (i.e. allowing {@code accessToken} to be optional)
 */
public class QuarkusHuggingFaceEmbeddingModel implements EmbeddingModel {

    public static final HuggingFaceClientFactory CLIENT_FACTORY = new QuarkusHuggingFaceClientFactory();

    private final HuggingFaceClient client;
    private final boolean waitForModel;

    private QuarkusHuggingFaceEmbeddingModel(Builder builder) {
        this.client = CLIENT_FACTORY.create(new HuggingFaceClientFactory.Input() {
            @Override
            public String apiKey() {
                return builder.accessToken;
            }

            @Override
            public String modelId() {
                return builder.modelId;
            }

            @Override
            public Duration timeout() {
                return builder.timeout;
            }
        });
        this.waitForModel = builder.waitForModel;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = new EmbeddingRequest(texts, waitForModel);

        List<float[]> response = client.embed(request);

        List<Embedding> embeddings = response.stream()
                .map(Embedding::from)
                .collect(toList());

        return Response.from(embeddings);
    }

    public static final class Builder {

        private String accessToken;
        private String modelId = SENTENCE_TRANSFORMERS_ALL_MINI_LM_L6_V2;
        private Duration timeout = Duration.ofSeconds(15);
        private Boolean waitForModel = true;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder waitForModel(Boolean waitForModel) {
            this.waitForModel = waitForModel;
            return this;
        }

        public QuarkusHuggingFaceEmbeddingModel build() {
            return new QuarkusHuggingFaceEmbeddingModel(this);
        }
    }
}

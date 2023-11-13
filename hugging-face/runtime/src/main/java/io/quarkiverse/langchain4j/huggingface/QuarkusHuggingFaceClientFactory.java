package io.quarkiverse.langchain4j.huggingface;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class QuarkusHuggingFaceClientFactory implements HuggingFaceClientFactory {

    @Override
    public HuggingFaceClient create(Input input) {
        throw new UnsupportedOperationException("Should not be called");
    }

    public HuggingFaceClient create(Input input, URI url) {
        HuggingFaceRestApi restApi = QuarkusRestClientBuilder.newBuilder()
                .baseUri(url)
                .connectTimeout(input.timeout().toSeconds(), TimeUnit.SECONDS)
                .readTimeout(input.timeout().toSeconds(), TimeUnit.SECONDS)
                .build(HuggingFaceRestApi.class);
        return new QuarkusHuggingFaceClient(restApi, input.apiKey());
    }

    public static class QuarkusHuggingFaceClient implements HuggingFaceClient {

        private final HuggingFaceRestApi restApi;
        private final String token;

        public QuarkusHuggingFaceClient(HuggingFaceRestApi restApi, String token) {
            this.restApi = restApi;
            this.token = token;
        }

        @Override
        public TextGenerationResponse chat(TextGenerationRequest request) {
            return generate(request);
        }

        @Override
        public TextGenerationResponse generate(TextGenerationRequest request) {
            return toOneResponse(restApi.generate(request, token));
        }

        private static TextGenerationResponse toOneResponse(List<TextGenerationResponse> responses) {
            if (responses != null && responses.size() == 1) {
                return responses.get(0);
            } else {
                throw new RuntimeException(
                        "Expected only one generated_text, but was: " + (responses == null ? 0 : responses.size()));
            }
        }

        @Override
        public List<float[]> embed(EmbeddingRequest request) {
            return restApi.embed(request, token);
        }
    }
}

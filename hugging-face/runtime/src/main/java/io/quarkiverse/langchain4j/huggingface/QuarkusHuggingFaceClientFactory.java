package io.quarkiverse.langchain4j.huggingface;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.config.ConfigProvider;

import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import io.quarkiverse.langchain4j.huggingface.runtime.config.Langchain4jHuggingFaceConfig;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class QuarkusHuggingFaceClientFactory implements HuggingFaceClientFactory {

    @Override
    public HuggingFaceClient create(Input input) {
        HuggingFaceRestApi restApi = QuarkusRestClientBuilder.newBuilder()
                .baseUri(determineBaseUrl())
                .connectTimeout(input.timeout().toSeconds(), TimeUnit.SECONDS)
                .readTimeout(input.timeout().toSeconds(), TimeUnit.SECONDS)
                .build(HuggingFaceRestApi.class);
        return new QuarkusHuggingFaceClient(restApi, input.modelId(), input.apiKey());
    }

    /**
     * This is an unclean way of passing the base URL to the REST Client and the reason it exists is that
     * currently {@link dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory.Input} does not contain
     * the {@code baseUrl} setting
     */
    private URI determineBaseUrl() {
        try {
            String baseUrl = Langchain4jHuggingFaceConfig.DEFAULT_BASE_URL;
            Optional<String> baseUrlOpt = ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.langchain4j.huggingface.base-url", String.class);
            if (baseUrlOpt.isPresent()) {
                baseUrl = baseUrlOpt.get();
            }
            return new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static class QuarkusHuggingFaceClient implements HuggingFaceClient {

        private final HuggingFaceRestApi restApi;
        private final String modelId;
        private final String token;

        public QuarkusHuggingFaceClient(HuggingFaceRestApi restApi, String modelId, String token) {
            this.restApi = restApi;
            this.modelId = modelId;
            this.token = token;
        }

        @Override
        public TextGenerationResponse chat(TextGenerationRequest request) {
            return generate(request);
        }

        @Override
        public TextGenerationResponse generate(TextGenerationRequest request) {
            return toOneResponse(restApi.generate(request, modelId, token));
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
            return restApi.embed(request, modelId, token);
        }
    }
}

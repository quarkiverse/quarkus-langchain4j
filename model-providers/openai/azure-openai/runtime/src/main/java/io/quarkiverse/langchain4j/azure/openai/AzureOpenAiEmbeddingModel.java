package io.quarkiverse.langchain4j.azure.openai;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static io.quarkiverse.langchain4j.azure.openai.Consts.DEFAULT_USER_AGENT;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.embedding.EmbeddingRequest;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiClient;

/**
 * Represents an OpenAI embedding model, hosted on Azure, such as text-embedding-ada-002.
 * <p>
 * Mandatory parameters for initialization are: {@code apiVersion}, {@code apiKey}, and either {@code endpoint} OR
 * {@code resourceName} and {@code deploymentName}.
 * <p>
 * There are two primary authentication methods to access Azure OpenAI:
 * <p>
 * 1. API Key Authentication: For this type of authentication, HTTP requests must include the
 * API Key in the "api-key" HTTP header.
 * <p>
 * 2. Azure Active Directory Authentication: For this type of authentication, HTTP requests must include the
 * authentication/access token in the "Authorization" HTTP header.
 * <p>
 * <a href="https://learn.microsoft.com/en-us/azure/ai-services/openai/reference">More information</a>
 * <p>
 */
public class AzureOpenAiEmbeddingModel implements EmbeddingModel, TokenCountEstimator {

    private static final int BATCH_SIZE = 16;

    private final OpenAiClient client;
    private final Integer maxRetries;
    private final Tokenizer tokenizer;

    public AzureOpenAiEmbeddingModel(String endpoint,
            String apiVersion,
            String apiKey,
            String adToken,
            Tokenizer tokenizer,
            Duration timeout,
            Integer maxRetries,
            Proxy proxy,
            Boolean logRequests,
            Boolean logResponses,
            String configName) {

        timeout = getOrDefault(timeout, ofSeconds(60));
        if (maxRetries < 1) {
            throw new IllegalArgumentException("max-retries must be at least 1");
        }

        this.client = ((QuarkusOpenAiClient.Builder) OpenAiClient.builder()
                .baseUrl(ensureNotBlank(endpoint, "endpoint"))
                .apiVersion(apiVersion)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses))
                .userAgent(DEFAULT_USER_AGENT)
                .azureAdToken(adToken)
                .azureApiKey(apiKey)
                .configName(configName)
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.tokenizer = tokenizer;
    }

    /**
     * Embeds the provided text segments, processing a maximum of 16 segments at a time.
     * For more information, refer to the documentation <a href=
     * "https://learn.microsoft.com/en-us/azure/ai-services/openai/faq#i-am-trying-to-use-embeddings-and-received-the-error--invalidrequesterror--too-many-inputs--the-max-number-of-inputs-is-1---how-do-i-fix-this-">here</a>.
     *
     * @param textSegments A list of text segments.
     * @return A list of corresponding embeddings.
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        List<Embedding> embeddings = new ArrayList<>();

        int inputTokenCount = 0;
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {

            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));

            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input(batch)
                    .build();

            EmbeddingResponse response = withRetry(() -> client.embedding(request).execute(), maxRetries);

            embeddings.addAll(response.data().stream()
                    .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                    .collect(toList()));

            inputTokenCount += response.usage().promptTokens();
        }

        return Response.from(
                embeddings,
                new TokenUsage(inputTokenCount));
    }

    @Override
    public int estimateTokenCount(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String apiVersion;
        private String apiKey;
        private Tokenizer tokenizer;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private String adToken;
        private String configName;

        /**
         * Sets the Azure OpenAI endpoint. This is a mandatory parameter.
         *
         * @param endpoint The Azure OpenAI endpoint in the format:
         *        https://{resource-name}.openai.azure.com/openai/deployments/{deployment-name}
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure OpenAI API version. This is a mandatory parameter.
         *
         * @param apiVersion The Azure OpenAI api version in the format: 2023-05-15
         * @return builder
         */
        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /**
         * Sets the Azure OpenAI API key. This is a mandatory parameter.
         *
         * @param apiKey The Azure OpenAI API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder adToken(String adToken) {
            this.adToken = adToken;
            return this;
        }

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public AzureOpenAiEmbeddingModel build() {
            return new AzureOpenAiEmbeddingModel(endpoint,
                    apiVersion,
                    apiKey,
                    adToken,
                    tokenizer,
                    timeout,
                    maxRetries,
                    proxy,
                    logRequests,
                    logResponses,
                    configName);
        }
    }
}

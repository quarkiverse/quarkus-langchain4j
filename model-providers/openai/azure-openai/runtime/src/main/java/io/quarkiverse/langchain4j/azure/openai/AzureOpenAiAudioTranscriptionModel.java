package io.quarkiverse.langchain4j.azure.openai;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static io.quarkiverse.langchain4j.azure.openai.Consts.DEFAULT_USER_AGENT;

import java.time.Duration;
import java.util.concurrent.Callable;

import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import dev.langchain4j.model.openai.internal.SyncOrAsync;
import dev.langchain4j.model.openai.internal.audio.transcription.AudioFile;
import dev.langchain4j.model.openai.internal.audio.transcription.OpenAiAudioTranscriptionRequest;
import dev.langchain4j.model.openai.internal.audio.transcription.OpenAiAudioTranscriptionResponse;
import io.quarkiverse.langchain4j.openai.common.QuarkusOpenAiClient;

public class AzureOpenAiAudioTranscriptionModel implements AudioTranscriptionModel {

    private final String modelName;
    private final Integer maxRetries;

    private final QuarkusOpenAiClient client;

    public AzureOpenAiAudioTranscriptionModel(String endpoint, String apiKey, String adToken, String apiVersion,
            String modelName, Duration timeout, Integer maxRetries, Boolean logRequests, Boolean logResponses,
            Boolean logCurl, String configName) {
        this.modelName = modelName;
        this.maxRetries = maxRetries;

        this.client = QuarkusOpenAiClient.builder()
                .baseUrl(ensureNotBlank(endpoint, "endpoint"))
                .apiVersion(apiVersion)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .logCurl(logCurl != null && logCurl)
                .userAgent(DEFAULT_USER_AGENT)
                .azureAdToken(adToken)
                .azureApiKey(apiKey)
                .configName(configName)
                .build();
    }

    @Override
    public AudioTranscriptionResponse transcribe(AudioTranscriptionRequest request) {
        var openAiRequest = OpenAiAudioTranscriptionRequest.builder()
                .model(modelName)
                .file(AudioFile.from(request.audio()))
                .language(request.language())
                .prompt(request.prompt())
                .temperature(request.temperature())
                .build();

        var response = withRetry(new AudioTranscriber(openAiRequest), maxRetries).execute();

        return AudioTranscriptionResponse.from(response.text());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String endpoint;
        private String apiKey;
        private String adToken;
        private String apiVersion;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Boolean logCurl;
        private String configName;

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder adToken(String adToken) {
            this.adToken = adToken;
            return this;
        }

        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
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

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logCurl(Boolean logCurl) {
            this.logCurl = logCurl;
            return this;
        }

        public Builder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public AzureOpenAiAudioTranscriptionModel build() {
            return new AzureOpenAiAudioTranscriptionModel(endpoint, apiKey, adToken, apiVersion, modelName, timeout,
                    maxRetries, logRequests, logResponses, logCurl, configName);
        }
    }

    private class AudioTranscriber implements Callable<SyncOrAsync<OpenAiAudioTranscriptionResponse>> {
        private final OpenAiAudioTranscriptionRequest request;

        private AudioTranscriber(OpenAiAudioTranscriptionRequest request) {
            this.request = request;
        }

        @Override
        public SyncOrAsync<OpenAiAudioTranscriptionResponse> call() {
            return client.audioTranscription(request);
        }
    }
}

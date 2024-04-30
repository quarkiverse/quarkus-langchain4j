package io.quarkiverse.langchain4j.openai;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.model.openai.OpenAiModelName.DALL_E_2;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.ai4j.openai4j.image.GenerateImagesRequest;
import dev.ai4j.openai4j.image.GenerateImagesResponse;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class QuarkusOpenAiImageModel implements ImageModel {
    private final String modelName;
    private final String size;
    private final String quality;
    private final String style;
    private final Optional<String> user;
    private final String responseFormat;
    private final Integer maxRetries;
    private final Optional<Path> persistDirectory;

    private final QuarkusOpenAiClient client;

    public QuarkusOpenAiImageModel(String baseUrl, String apiKey, String organizationId, String modelName, String size,
            String quality, String style, Optional<String> user, String responseFormat, Duration timeout,
            Integer maxRetries, Boolean logRequests, Boolean logResponses,
            Optional<Path> persistDirectory) {
        this.modelName = modelName;
        this.size = size;
        this.quality = quality;
        this.style = style;
        this.user = user;
        this.responseFormat = responseFormat;
        this.maxRetries = maxRetries;
        if (this.maxRetries < 1) {
            throw new IllegalArgumentException("max-retries must be at least 1");
        }
        this.persistDirectory = persistDirectory;

        this.client = QuarkusOpenAiClient.builder()
                .baseUrl(baseUrl)
                .openAiApiKey(apiKey)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    @Override
    public Response<Image> generate(String prompt) {
        GenerateImagesRequest request = requestBuilder(prompt).build();

        GenerateImagesResponse response = withRetry(() -> client.imagesGeneration(request), maxRetries).execute();
        persistIfNecessary(response);

        return Response.from(fromImageData(response.data().get(0)));
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        GenerateImagesRequest request = requestBuilder(prompt).n(n).build();

        GenerateImagesResponse response = withRetry(() -> client.imagesGeneration(request), maxRetries).execute();
        persistIfNecessary(response);

        return Response.from(
                response.data().stream().map(QuarkusOpenAiImageModel::fromImageData).collect(Collectors.toList()));
    }

    private void persistIfNecessary(GenerateImagesResponse response) {
        if (persistDirectory.isEmpty()) {
            return;
        }
        Path persistTo = persistDirectory.get();
        try {
            Files.createDirectories(persistTo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (GenerateImagesResponse.ImageData data : response.data()) {
            try {
                data.url(
                        data.url() != null
                                ? FilePersistor.persistFromUri(data.url(), persistTo).toUri()
                                : FilePersistor.persistFromBase64String(data.b64Json(), persistTo).toUri());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static Image fromImageData(GenerateImagesResponse.ImageData data) {
        return Image.builder().url(data.url()).base64Data(data.b64Json()).revisedPrompt(data.revisedPrompt()).build();
    }

    private GenerateImagesRequest.Builder requestBuilder(String prompt) {
        var builder = GenerateImagesRequest
                .builder()
                .prompt(prompt)
                .size(size)
                .quality(quality)
                .style(style)
                .responseFormat(responseFormat);

        if (DALL_E_2.equals(modelName)) {
            builder.model(dev.ai4j.openai4j.image.ImageModel.DALL_E_2);
        }
        if (user.isPresent()) {
            builder.user(user.get());
        }

        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String modelName;
        private String size;
        private String quality;
        private String style;
        private Optional<String> user;
        private String responseFormat;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Optional<Path> persistDirectory;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
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

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder size(String size) {
            this.size = size;
            return this;
        }

        public Builder quality(String quality) {
            this.quality = quality;
            return this;
        }

        public Builder style(String style) {
            this.style = style;
            return this;
        }

        public Builder user(Optional<String> user) {
            this.user = user;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder persistDirectory(Optional<Path> persistDirectory) {
            this.persistDirectory = persistDirectory;
            return this;
        }

        public QuarkusOpenAiImageModel build() {
            return new QuarkusOpenAiImageModel(baseUrl, apiKey, organizationId, modelName, size, quality, style, user,
                    responseFormat, timeout, maxRetries, logRequests, logResponses,
                    persistDirectory);
        }
    }

    /**
     * Copied from {@code dev.ai4j.openai4j.FilePersistor}
     */
    private static class FilePersistor {

        static Path persistFromUri(URI uri, Path destinationFolder) {
            try {
                Path fileName = Paths.get(uri.getPath()).getFileName();
                Path destinationFilePath = destinationFolder.resolve(fileName);
                try (InputStream inputStream = uri.toURL().openStream()) {
                    java.nio.file.Files.copy(inputStream, destinationFilePath, StandardCopyOption.REPLACE_EXISTING);
                }

                return destinationFilePath;
            } catch (IOException e) {
                throw new RuntimeException("Error persisting file from URI: " + uri, e);
            }
        }

        public static Path persistFromBase64String(String base64EncodedString, Path destinationFolder) throws IOException {
            byte[] decodedBytes = Base64.getDecoder().decode(base64EncodedString);
            Path destinationFile = destinationFolder.resolve(randomFileName());

            Files.write(destinationFile, decodedBytes, StandardOpenOption.CREATE);

            return destinationFile;
        }

        private static String randomFileName() {
            return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 20);
        }
    }
}

package io.quarkiverse.langchain4j.openai;

import static dev.langchain4j.internal.RetryUtils.withRetry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.Proxy;
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

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.image.ImageData;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.openai.common.QuarkusOpenAiClient;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class QuarkusOpenAiImageModel implements ImageModel {
    private final String modelName;
    private final String size;
    private final String quality;
    private final Optional<String> user;
    private final Integer maxRetries;
    private final Optional<Path> persistDirectory;
    private final String outputFormat;
    private final String background;
    private final Integer outputCompression;
    private final String moderation;

    private final QuarkusOpenAiClient client;

    public QuarkusOpenAiImageModel(Builder builder) {
        this.modelName = builder.modelName;
        this.size = builder.size;
        this.quality = builder.quality;
        this.user = builder.user;
        this.maxRetries = builder.maxRetries;
        if (this.maxRetries < 1) {
            throw new IllegalArgumentException("max-retries must be at least 1");
        }
        this.persistDirectory = builder.persistDirectory;
        this.outputFormat = builder.outputFormat;
        this.background = builder.background;
        this.outputCompression = builder.outputCompression;
        this.moderation = builder.moderation;

        this.client = QuarkusOpenAiClient.builder()
                .baseUrl(builder.baseUrl)
                .configName(builder.configName)
                .tlsConfigurationName(builder.tlsConfigurationName)
                .openAiApiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .callTimeout(builder.timeout)
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .writeTimeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logCurl(builder.logCurl != null && builder.logCurl)
                .proxy(builder.proxy)
                .build();
    }

    @Override
    public Response<Image> generate(String prompt) {
        var request = buildRequest(prompt);

        GenerateImagesResponse response = withRetry(() -> client.imagesGeneration(request), maxRetries).execute();
        persistIfNecessary(response);

        return Response.from(fromImageData(response.data().get(0)));
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        var request = buildRequest(prompt, n);

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
        for (ImageData data : response.data()) {
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

    private static Image fromImageData(ImageData data) {
        return Image.builder().url(data.url()).base64Data(data.b64Json()).revisedPrompt(data.revisedPrompt()).build();
    }

    private GenerateImagesRequest buildRequest(String prompt) {
        return buildRequest(prompt, 1);
    }

    private GenerateImagesRequest buildRequest(String prompt, int n) {
        var builder = GenerateImagesRequest.builder()
                .prompt(prompt)
                .model(modelName)
                .n(n)
                .size(size)
                .quality(quality);

        if (user.isPresent()) {
            builder.user(user.get());
        }
        if (outputFormat != null) {
            builder.outputFormat(outputFormat);
        }
        if (background != null) {
            builder.background(background);
        }
        if (outputCompression != null) {
            builder.outputCompression(outputCompression);
        }
        if (moderation != null) {
            builder.moderation(moderation);
        }

        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl;
        private String configName;
        private String tlsConfigurationName;
        private String apiKey;
        private String organizationId;
        private String modelName;
        private String size;
        private String quality;
        private Optional<String> user;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Boolean logCurl;
        private Optional<Path> persistDirectory;
        private Proxy proxy;
        private String outputFormat;
        private String background;
        private Integer outputCompression;
        private String moderation;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public Builder tlsConfigurationName(String tlsConfigurationName) {
            this.tlsConfigurationName = tlsConfigurationName;
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

        public Builder logCurl(Boolean logCurl) {
            this.logCurl = logCurl;
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

        public Builder user(Optional<String> user) {
            this.user = user;
            return this;
        }

        public Builder persistDirectory(Optional<Path> persistDirectory) {
            this.persistDirectory = persistDirectory;
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder outputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        public Builder background(String background) {
            this.background = background;
            return this;
        }

        public Builder outputCompression(Integer outputCompression) {
            this.outputCompression = outputCompression;
            return this;
        }

        public Builder moderation(String moderation) {
            this.moderation = moderation;
            return this;
        }

        public QuarkusOpenAiImageModel build() {
            return new QuarkusOpenAiImageModel(this);
        }
    }

    /**
     * Copied from {@code dev.langchain4j.model.openai.internal.FilePersistor}
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

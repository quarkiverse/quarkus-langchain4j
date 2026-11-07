package io.quarkiverse.langchain4j.gpullama3;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jboss.logging.Logger;

/**
 * A registry for managing GPULlama3.java models on local disk.
 * <p>
 * <a href="https://huggingface.co/beehive-lab">Beehive Lab</a> HuggingFace repository.
 * <p>
 * Reused implementation of {@link io.quarkiverse.langchain4j.llama3.Llama3ModelRegistry}
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class GPULlama3ModelRegistry {

    private static final Logger LOG = Logger.getLogger(GPULlama3ModelRegistry.class);

    private static final String DEFAULT_MODEL_CACHE_PATH = System.getProperty("user.home", "") + File.separator + ".langchain4j"
            + File.separator + "models";

    public static String FINISHED_MARKER = ".finished";

    private final Path modelCachePath;

    private GPULlama3ModelRegistry(Path modelCachePath) {
        this.modelCachePath = modelCachePath;
        if (!Files.exists(modelCachePath)) {
            try {
                Files.createDirectories(modelCachePath);
            } catch (IOException e) {
                throw new IOError(e);
            }
        }
    }

    public static GPULlama3ModelRegistry getOrCreate(Optional<Path> modelCachePath) {
        return new GPULlama3ModelRegistry(modelCachePath.orElse(Path.of(DEFAULT_MODEL_CACHE_PATH)));
    }

    public Path constructModelDirectoryPath(ModelInfo modelInfo) {
        return Paths.get(modelCachePath.toAbsolutePath().toString(), modelInfo.owner() + "_" + modelInfo.name());
    }

    public Path constructGgufModelFilePath(ModelInfo modelInfo, String quantization) {
        String effectiveFileName = getEffectiveFileName(modelInfo, quantization);
        Path modelDirectory = constructModelDirectoryPath(modelInfo);
        return modelDirectory.resolve(effectiveFileName);
    }

    public Path downloadModel(String modelName, String quantization, Optional<String> authToken,
            Optional<ProgressReporter> maybeProgressReporter)
            throws IOException, InterruptedException {
        ModelInfo modelInfo = ModelInfo.from(modelName);

        String effectiveFileName = getEffectiveFileName(modelInfo, quantization);
        Path modelDirectory = constructModelDirectoryPath(modelInfo);
        Path result = modelDirectory.resolve(effectiveFileName);
        if (Files.exists(result) && Files.exists(modelDirectory.resolve(FINISHED_MARKER))) {
            return result;
        }

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        URI uri = URI.create(
                String.format("https://huggingface.co/%s/%s/resolve/main/%s", modelInfo.owner(), modelInfo.name(),
                        effectiveFileName));
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        HttpResponse<InputStream> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (httpResponse.statusCode() != 200) {
            throw new RuntimeException(
                    "Unable to download model " + modelName + ". Response code from " + uri + " is : "
                            + httpResponse.statusCode());
        }
        Files.createDirectories(result.getParent());
        long totalBytes = httpResponse.headers().firstValueAsLong("content-length").orElse(-1);
        ProgressReporter progressReporter = maybeProgressReporter.orElse((filename, sizeDownloaded, totalSize) -> {
        });

        if (maybeProgressReporter.isEmpty()) {
            LOG.info("Downloading file " + result.toAbsolutePath());
        }
        String resultFileName = result.getFileName().toString();
        progressReporter.update(resultFileName, 0L, totalBytes);

        try (CountingInputStream inStream = new CountingInputStream(httpResponse.body())) {
            CompletableFuture<Long> cf = CompletableFuture.supplyAsync(() -> {
                try {
                    return Files.copy(inStream, result, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            while (!cf.isDone()) {
                progressReporter.update(resultFileName, inStream.count, totalBytes);
            }
            if (cf.isCompletedExceptionally()) {
                progressReporter.update(resultFileName, inStream.count, totalBytes);
            } else {
                progressReporter.update(resultFileName, totalBytes, totalBytes);
            }

            try {
                cf.get();
            } catch (Throwable e) {
                throw new IOException("Failed to download file: " + resultFileName, e);
            }
            if (maybeProgressReporter.isEmpty()) {
                LOG.info("Downloaded file " + result.toAbsolutePath());
            }
        }

        // create a finished marker
        Files.createFile(modelDirectory.resolve(FINISHED_MARKER));
        return result;
    }

    private String getEffectiveFileName(ModelInfo modelInfo, String quantization) {
        String effectiveFileName = modelInfo.name();
        if (effectiveFileName.endsWith("-GGUF")) {
            effectiveFileName = effectiveFileName.substring(0, effectiveFileName.length() - 5);
        }
        effectiveFileName = effectiveFileName + "-" + quantization + ".gguf";
        return effectiveFileName;
    }

    /**
     * This interface reports the progress of a .gguf file download.
     * The implementation of the update method is used to communicate this progress.
     */
    public interface ProgressReporter {

        void update(String filename, long sizeDownloaded, long totalSize);
    }

    /**
     * ModelInfo is a simple data class that represents a model's owner and name.
     * <p>
     * Reused implementation of {@link io.quarkiverse.langchain4j.llama3.Llama3ModelRegistry}
     */
    public record ModelInfo(String owner, String name) {

        public static ModelInfo from(String modelName) {
            String[] parts = modelName.split("/");
            if (parts.length == 0 || parts.length > 2) {
                throw new IllegalArgumentException("Model must be in the form owner/name");
            }

            String owner;
            String name;

            if (parts.length == 1) {
                owner = null;
                name = modelName;
            } else {
                owner = parts[0];
                name = parts[1];
            }

            return new ModelInfo(owner, name);
        }

        public String toFileName() {
            return owner + "_" + name;
        }
    }

    /**
     * An {@link InputStream} that counts the number of bytes read.
     *
     * @author Chris Nokleberg
     *
     *         Copied from Guava
     */
    public static final class CountingInputStream extends FilterInputStream {

        private long count;
        private long mark = -1;

        /**
         * Wraps another input stream, counting the number of bytes read.
         *
         * @param in the input stream to be wrapped
         */
        public CountingInputStream(InputStream in) {
            super(Objects.requireNonNull(in));
        }

        /** Returns the number of bytes read. */
        public long getCount() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int result = in.read();
            if (result != -1) {
                count++;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = in.read(b, off, len);
            if (result != -1) {
                count += result;
            }
            return result;
        }

        @Override
        public long skip(long n) throws IOException {
            long result = in.skip(n);
            count += result;
            return result;
        }

        @Override
        public synchronized void mark(int readlimit) {
            in.mark(readlimit);
            mark = count;
            // it's okay to mark even if mark isn't supported, as reset won't work
        }

        @Override
        public synchronized void reset() throws IOException {
            if (!in.markSupported()) {
                throw new IOException("Mark not supported");
            }
            if (mark == -1) {
                throw new IOException("Mark not set");
            }

            in.reset();
            count = mark;
        }
    }
}

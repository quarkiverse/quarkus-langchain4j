package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;
import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;
import static io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionType.ASSEMBLY_MD;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.watsonx.WatsonxUtils;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionDataReference;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionSteps;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionType;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionResponse.ServiceError;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionResponse.Status;
import io.quarkiverse.langchain4j.watsonx.client.COSRestApi;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.exception.COSException;
import io.quarkiverse.langchain4j.watsonx.exception.TextExtractionException;

/**
 * This class provides methods for extracting text from high-value business documents, making them
 * more accessible to AI models or enabling the identification of key information.
 * <p>
 * The API supports text extraction from the following file types:
 * </p>
 * <ul>
 * <li>PDF</li>
 * <li>GIF</li>
 * <li>JPG</li>
 * <li>PNG</li>
 * <li>TIFF</li>
 * </ul>
 * <p>
 * The extracted text can be output in the following formats:
 * </p>
 * <ul>
 * <li>JSON</li>
 * <li>Markdown</li>
 * </ul>
 */
public class TextExtraction {

    private static final Logger logger = Logger.getLogger(TextExtraction.class);

    public record Reference(String connection, String bucket) {
        public Reference {
            requireNonNull(connection);
        }

        public Reference(String connection) {
            this(connection, null);
        }
    };

    final private WatsonxRestApi watsonxClient;
    final private COSRestApi cosClient;
    final private Reference documentReference;
    final private Reference resultReference;
    final private String projectId, spaceId, version;

    /**
     * Constructs a {@code TextExtraction} instance with the required parameters to perform text
     * extraction from documents stored in IBM Cloud Object Storage (COS). This constructor initializes
     * the necessary references, project details, and client instances for interacting with IBM COS and
     * Watsonx AI services.
     * <p>
     * <strong>Default Bucket:</strong>
     * <p>
     * The {@code documentReference.bucket} and {@code resultReference.bucket} fields are used as the
     * default buckets for uploading documents to COS and they will serve as the target location for the
     * following:
     * <ul>
     * <li>{@code documentReference.bucket}: The default bucket for uploading local documents to
     * COS.</li>
     * <li>{@code resultReference.bucket}: The default bucket for uploading extracted documents to
     * COS.</li>
     * </ul>
     */
    public TextExtraction(
            Reference documentReference, Reference resultReference,
            String projectId, String spaceId, String version,
            COSRestApi cosClient, WatsonxRestApi watsonxClient) {

        requireNonNull(cosClient);
        requireNonNull(documentReference);
        requireNonNull(resultReference);
        requireNonNull(watsonxClient);

        if (nonNull(documentReference) && (isNull(documentReference.bucket) || documentReference.bucket.isBlank()))
            throw new IllegalArgumentException(
                    "The TextExtraction constructor needs a non-empty or null value for \"documentReference.bucket\", this value will be the default bucket used to upload the local documents to the Cloud Object Storage.");

        if (nonNull(resultReference) && (isNull(resultReference.bucket) || resultReference.bucket.isBlank()))
            throw new IllegalArgumentException(
                    "The TextExtraction constructor needs a non-empty or null value for \"resultReference.bucket\", this value will be the default bucket used to upload the extracted documents to the Cloud Object Storage.");

        this.documentReference = documentReference;
        this.resultReference = resultReference;
        this.projectId = projectId;
        this.spaceId = spaceId;
        this.version = version;
        this.cosClient = cosClient;
        this.watsonxClient = watsonxClient;
    }

    /**
     * Starts the asynchronous text extraction process for a document stored in IBM Cloud Object Storage
     * (COS). The extracted text is saved as a new <b>Markdown</b> file in COS, preserving the original
     * filename but using the {@code .md } extension. To customize the output behavior, use the method
     * with the {@link Options} parameter.
     *
     * <pre>
     * {@code
     * String startExtraction(String absolutePath, Options options)
     * }
     * </pre>
     *
     * <b>Note:</b> This method does not return the extracted value. Use {@code extractAndFetch} to
     * extract the text immediately.
     *
     * @param absolutePath The COS path of the document to extract text from.
     * @param languages Language codes to guide the text extraction process.
     * @return The unique identifier of the text extraction process.
     */
    public String startExtraction(String absolutePath, List<Language> languages) throws TextExtractionException {
        requireNonNull(languages);
        return startExtraction(absolutePath, Options.create(languages));
    }

    /**
     * Starts the asynchronous text extraction process for a document stored in IBM Cloud Object Storage
     * (COS). The extracted text is saved as a new <b>Markdown</b> file in COS, preserving the original
     * filename but using the {@code .md} extension by default. Output behavior can be customized using
     * the {@link Options} parameter.
     * <p>
     * <b>Note:</b> This method does not return the extracted text. Use {@code extractAndFetch} to
     * extract the text immediately.
     *
     * @param absolutePath The COS path of the document to extract text from.
     * @param options The configuration options for text extraction.
     * @return The unique identifier of the text extraction process.
     */
    public String startExtraction(String absolutePath, Options options) throws TextExtractionException {
        requireNonNull(options);
        return startExtraction(absolutePath, options, false).metadata().id();
    }

    /**
     * Uploads a local file to IBM Cloud Object Storage (COS) and starts the asynchronous text
     * extraction process. The extracted text is saved as a new <b>Markdown</b> file in COS, preserving
     * the original filename but using the {@code .md} extension by default. To customize the output
     * behavior you can use the {@link Options} parameter.
     *
     * <pre>
     * {@code
     * String uploadAndStartExtraction(File file, Options options);
     * }
     * </pre>
     *
     * <b>Note:</b> This method does not return the extracted text. Use {@code uploadExtractAndFetch} to
     * extract the text immediately.
     *
     * @param file The local file to be uploaded and processed.
     * @param languages Language codes to guide the text extraction process.
     * @return The unique identifier of the text extraction process.
     */
    public String uploadAndStartExtraction(File file, List<Language> languages) throws TextExtractionException {
        requireNonNull(languages);
        return uploadAndStartExtraction(file, Options.create(languages));
    }

    /**
     * Uploads a local file to IBM Cloud Object Storage (COS) and starts the asynchronous text
     * extraction process. The extracted text is saved as a new <b>Markdown</b> file in COS, preserving
     * the original filename but using the {@code .md} extension by default. Output behavior can be
     * customized using the {@link Options} parameter.
     *
     * <p>
     * <b>Note:</b> This method does not return the extracted text. Use {@code uploadExtractAndFetch} to
     * extract the text immediately.
     *
     * @param file The local file to be uploaded and processed.
     * @param options The configuration options for text extraction.
     * @return The unique identifier of the text extraction process.
     */
    public String uploadAndStartExtraction(File file, Options options) throws TextExtractionException {
        requireNonNull(options);
        requireNonNull(file);

        if (file.isDirectory())
            throw new TextExtractionException("directory_not_allowed", "The file can not be a directory");

        try {
            upload(new BufferedInputStream(new FileInputStream(file)), file.getName(), options, false);
            return startExtraction(file.getName(), options);
        } catch (FileNotFoundException e) {
            throw new TextExtractionException("file_not_found", e.getMessage(), e);
        }
    }

    /**
     * Uploads an InputStream to IBM Cloud Object Storage (COS) and starts the asynchronous text
     * extraction process. The extracted text is saved as a new <b>Markdown</b> file in COS, preserving
     * the original filename but using the {@code .md} extension by default. To customize the output
     * behavior you can use the {@link Options} parameter.
     *
     * <pre>
     * {@code
     * String uploadAndStartExtraction(InputStream is, String fileName, Options options);
     * }
     * </pre>
     *
     * <b>Note:</b> This method does not return the extracted text. Use {@code uploadExtractAndFetch} to
     * extract the text immediately.
     *
     * @param is The input stream of the file to be uploaded and processed.
     * @param fileName The name of the file to be uploaded and processed.
     * @param languages Language codes to guide the text extraction process.
     * @return The unique identifier of the text extraction process.
     */
    public String uploadAndStartExtraction(InputStream is, String fileName, List<Language> languages)
            throws TextExtractionException {
        requireNonNull(languages);
        return uploadAndStartExtraction(is, fileName, Options.create(languages));
    }

    /**
     * Uploads an InputStream to IBM Cloud Object Storage (COS) and starts the asynchronous text
     * extraction process. The extracted text is saved as a new <b>Markdown</b> file in COS, preserving
     * the original filename but using the {@code .md} extension by default. Output behavior can be
     * customized using the {@link Options} parameter.
     *
     * <p>
     * <b>Note:</b> This method does not return the extracted text. Use {@code uploadExtractAndFetch} to
     * extract the text immediately.
     *
     * @param is The input stream of the file to be uploaded and processed.
     * @param fileName The name of the file to be uploaded and processed.
     * @param options The configuration options for text extraction.
     * @return The unique identifier of the text extraction process.
     */
    public String uploadAndStartExtraction(InputStream is, String fileName, Options options)
            throws TextExtractionException {
        requireNonNull(options);
        upload(is, fileName, options, false);
        return startExtraction(fileName, options);
    }

    /**
     * Starts the text extraction process for a file that is already present in Cloud Object Storage
     * (COS) and returns the extracted text value. The extracted text is saved as a new <b>Markdown</b>
     * file in COS, preserving the original filename but using the {@code .md} extension by default. To
     * customize the output behavior, use the method with the {@link Options} parameter.
     *
     * <pre>
     * {@code
     * String extractAndFetch(String absolutePath, Options options);
     * }
     * </pre>
     *
     * <b>Note:</b> The default timeout value is set to 60 seconds.
     *
     * @param absolutePath The absolute path of the file in Cloud Object Storage.
     * @param languages Language codes to guide the text extraction process.
     * @return The text extracted.
     */
    public String extractAndFetch(String absolutePath, List<Language> languages) throws TextExtractionException {
        requireNonNull(languages);
        return extractAndFetch(absolutePath, Options.create(languages));
    }

    /**
     * Starts the text extraction process for a file that is already present in Cloud Object Storage
     * (COS) and returns the extracted text value. The extracted text is saved as a new <b>Markdown</b>
     * file in COS, preserving the original filename but using the {@code .md} extension by default.
     * Output behavior can be customized using the {@link Options} parameter.
     *
     * <p>
     * <li><b>Note:</b> This method waits until the extraction process is complete and returns the
     * extracted value. The default timeout value is set to 60 seconds.
     *
     * @param absolutePath The COS path of the document to extract text from.
     * @param options Configuration options, including cleanup behavior.
     * @return The text extracted.
     */
    public String extractAndFetch(String absolutePath, Options options) throws TextExtractionException {
        requireNonNull(options);
        var textExtractionResponse = startExtraction(absolutePath, options, true);
        return getExtractedText(textExtractionResponse, options);
    }

    /**
     * Uploads a local file to IBM Cloud Object Storage (COS), starts text extraction process and
     * returns the extracted text value. The extracted text is saved as a new <b>Markdown</b> file in
     * COS, preserving the original filename but using the {@code .md} extension by default. To
     * customize the output behavior, use the method with the {@link Options} parameter.
     *
     * <pre>
     * {@code
     * String uploadExtractAndFetch(File file, Options options);
     * }
     * </pre>
     *
     * <li><b>Note:</b> This method waits until the extraction process is complete and returns the
     * extracted value. The default timeout value is set to 60 seconds.
     *
     * @param file The local file to be uploaded and processed.
     * @param languages Language codes to guide the text extraction process.
     * @return The text extracted.
     */
    public String uploadExtractAndFetch(File file, List<Language> languages) throws TextExtractionException {
        requireNonNull(languages);
        return uploadExtractAndFetch(file, Options.create(languages));
    }

    /**
     * Uploads a local file to IBM Cloud Object Storage (COS) and starts the asynchronous text
     * extraction process. The extracted text is saved as a new <b>Markdown</b> file in COS, preserving
     * the original filename but using the {@code .md} extension by default. Output behavior can be
     * customized using the {@link Options} parameter.
     *
     * <p>
     * <li><b>Notes:</b> This method waits until the extraction process is complete and returns the
     * extracted value. The default timeout value is set to 60 seconds.
     *
     * @param file The local file to be uploaded and processed.
     * @param options Configuration options, including cleanup behavior.
     * @return The text extracted.
     */
    public String uploadExtractAndFetch(File file, Options options) throws TextExtractionException {
        requireNonNull(options);
        try {
            upload(new BufferedInputStream(new FileInputStream(file)), file.getName(), options, true);
        } catch (FileNotFoundException e) {
            throw new TextExtractionException("file_not_found", e.getMessage(), e);
        }
        return extractAndFetch(file.getName(), options);
    }

    /**
     * Uploads an InputStream to IBM Cloud Object Storage (COS), starts text extraction process and
     * returns the extracted text value. The extracted text is saved as a new <b>Markdown</b> file in
     * COS, preserving the original filename but using the {@code .md} extension by default. To
     * customize the output behavior, use the method with the {@link Options} parameter.
     *
     * <pre>
     * {@code
     * String uploadExtractAndFetch(InputStream is, String fileName, Options options);
     * }
     * </pre>
     *
     * <li><b>Note:</b> This method waits until the extraction process is complete and returns the
     * extracted value. The default timeout value is set to 60 seconds.
     *
     * @param is The input stream of the file to be uploaded and processed.
     * @param fileName The name of the file to be uploaded and processed.
     * @param languages Language codes to guide the text extraction process.
     * @return The text extracted.
     */
    public String uploadExtractAndFetch(InputStream is, String fileName, List<Language> languages)
            throws TextExtractionException {
        requireNonNull(languages);
        return uploadExtractAndFetch(is, fileName, Options.create(languages));
    }

    /**
     * Uploads an InputStream to IBM Cloud Object Storage (COS) and starts the asynchronous text
     * extraction process. The extracted text is saved as a new <b>Markdown</b> file in COS, preserving
     * the original filename but using the {@code .md} extension by default. Output behavior can be
     * customized using the {@link Options} parameter.
     *
     * <p>
     * <li><b>Note:</b> This method waits until the extraction process is complete and returns the
     * extracted value. The default timeout value is set to 60 seconds.
     *
     * @param is The input stream of the file to be uploaded and processed.
     * @param fileName The name of the file to be uploaded and processed.
     * @param options Configuration options, including cleanup behavior.
     * @return The text extracted.
     */
    public String uploadExtractAndFetch(InputStream is, String fileName, Options options)
            throws TextExtractionException {
        requireNonNull(options);
        upload(is, fileName, options, true);
        return extractAndFetch(fileName, options);
    }

    /**
     * Retrieves the current status of a text extraction process.
     *
     * @param id The unique identifier of the extraction process.
     * @return A {@link TextExtractionResponse} containing the status and details of the extraction job.
     */
    public TextExtractionResponse checkExtractionStatus(String id) throws TextExtractionException {
        return retryOn(new Callable<TextExtractionResponse>() {
            @Override
            public TextExtractionResponse call() throws Exception {
                return watsonxClient.getTextExtractionDetails(id, spaceId, projectId, version);
            }
        });
    }

    /**
     * Deletes a file from the specified bucket.
     *
     * @param bucketName the name of the storage bucket from which the file should be deleted.
     * @param path the path of the file to be deleted within the bucket.
     * @throws COSException if an error occurs while attempting to delete the file.
     */
    public void deleteFile(String bucketName, String path) throws COSException {
        deleteFile(bucketName, path, Duration.ofMinutes(1));
    }

    /**
     * Deletes a file from the specified bucket.
     *
     * @param bucketName the name of the storage bucket from which the file should be deleted.
     * @param path the path of the file to be deleted within the bucket.
     * @param timeout the maximum duration to wait for the deletion operation to complete.
     * @throws COSException if an error occurs while attempting to delete the file.
     */
    public void deleteFile(String bucketName, String path, Duration timeout) throws COSException {
        requireNonNull(bucketName);
        requireNonNull(path);
        timeout = isNull(timeout) ? Duration.ofMinutes(1) : timeout;
        cosClient.deleteFile(bucketName, path)
                .onFailure(WatsonxUtils::isTokenExpired).retry().atMost(1)
                .await().atMost(timeout);
    }

    //
    // Upload a stream to the Cloud Object Storage.
    //
    private void upload(InputStream is, String fileName, Options options, boolean waitForExtraction) {
        requireNonNull(is);
        if (isNull(fileName) || fileName.isBlank())
            throw new IllegalArgumentException("The file name can not be null or empty");

        boolean removeOutputFile = options.removeOutputFile.orElse(false);
        boolean removeUploadedFile = options.removeUploadedFile.orElse(false);

        if (!waitForExtraction && (removeOutputFile || removeUploadedFile))
            throw new IllegalArgumentException(
                    "The asynchronous version of startExtraction doesn't allow the use of the \"removeOutputFile\" and \"removeUploadedFile\" options");

        Reference documentReference = firstOrDefault(this.documentReference, options.documentReference);
        retryOn(new Callable<Response>() {
            @Override
            public Response call() throws Exception {
                return cosClient.createFile(documentReference.bucket, fileName, is);
            }
        });
    }

    private TextExtractionResponse startExtraction(String absolutePath, Options options, boolean waitUntilJobIsDone)
            throws TextExtractionException {
        requireNonNull(absolutePath);
        requireNonNull(options);

        if (options.languages.isEmpty())
            throw new IllegalArgumentException(
                    "To start the extraction process, you must specify at least one language");

        boolean removeOutputFile = options.removeOutputFile.orElse(false);
        boolean removeUploadedFile = options.removeUploadedFile.orElse(false);

        if (!waitUntilJobIsDone && (removeOutputFile || removeUploadedFile))
            throw new IllegalArgumentException(
                    "The asynchronous version of startExtraction doesn't allow the use of the \"removeOutputFile\" and \"removeUploadedFile\" options");

        if (isNull(options.outputFileName) || options.outputFileName.isBlank()) {

            String extension = switch (options.type) {
                case ASSEMBLY_JSON -> ".json";
                case ASSEMBLY_MD -> ".md";
            };

            var index = absolutePath.lastIndexOf(".");
            if (index > 0) {
                options.outputFileName = absolutePath.substring(0, index) + extension;
            } else {
                options.outputFileName = absolutePath + extension;
            }
        }

        Reference documentReference = firstOrDefault(this.documentReference, options.documentReference);
        Reference resultsReference = firstOrDefault(this.resultReference, options.resultsReference);

        var request = TextExtractionRequest.builder()
                .documentReference(
                        TextExtractionDataReference.of(documentReference.connection, absolutePath,
                                documentReference.bucket))
                .resultsReference(TextExtractionDataReference.of(resultsReference.connection, options.outputFileName,
                        resultsReference.bucket))
                .steps(TextExtractionSteps.of(options.languages, options.tableProcessing))
                .type(options.type)
                .projectId(projectId)
                .spaceId(spaceId)
                .build();

        TextExtractionResponse response = retryOn(new Callable<TextExtractionResponse>() {
            @Override
            public TextExtractionResponse call() throws Exception {
                return watsonxClient.startTextExtractionJob(request, version);
            }
        });

        if (!waitUntilJobIsDone)
            return response;

        Status status;
        long sleepTime = 100;
        LocalTime endTime = LocalTime.now().plus(options.timeout);

        do {

            if (LocalTime.now().isAfter(endTime))
                throw new TextExtractionException("timeout",
                        "Execution to extract %s file took longer than the timeout set by %s milliseconds"
                                .formatted(absolutePath, options.timeout.toMillis()));

            try {

                Thread.sleep(sleepTime);
                sleepTime *= 2;
                sleepTime = Math.min(sleepTime, 3000);

            } catch (Exception e) {
                throw new TextExtractionException("interrupted", e.getMessage());
            }

            var processId = response.metadata().id();
            response = retryOn(new Callable<TextExtractionResponse>() {
                @Override
                public TextExtractionResponse call() throws Exception {
                    return watsonxClient.getTextExtractionDetails(processId, spaceId, projectId, version);
                }
            });

            status = response.entity().results().status();

        } while (status != Status.FAILED && status != Status.COMPLETED);

        return response;
    }

    private String getExtractedText(TextExtractionResponse textExtractionResponse, Options options)
            throws TextExtractionException {
        requireNonNull(textExtractionResponse);
        requireNonNull(options);

        String uploadedPath = textExtractionResponse.entity().documentReference().location().fileName();
        String outputPath = textExtractionResponse.entity().resultsReference().location().fileName();
        Status status = textExtractionResponse.entity().results().status();
        boolean removeUploadedFile = options.removeUploadedFile.orElse(false);
        boolean removeOutputFile = options.removeOutputFile.orElse(false);

        Reference documentReference = firstOrDefault(this.documentReference, options.documentReference);
        String documentBucketName = documentReference.bucket;

        Reference resultsReference = firstOrDefault(this.resultReference, options.resultsReference);
        String resultsBucketName = resultsReference.bucket;

        try {

            String extractedFile = switch (status) {
                case COMPLETED -> retryOn(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return cosClient.getFileContent(resultsBucketName, options.outputFileName);
                    }
                });
                case FAILED -> {
                    ServiceError error = textExtractionResponse.entity().results().error();
                    throw new TextExtractionException(error.code(), error.message());
                }
                default -> throw new TextExtractionException("generic_error", "Status %s not managed".formatted(status));
            };

            if (removeOutputFile) {
                cosClient.deleteFile(resultsBucketName, outputPath)
                        .onFailure(WatsonxUtils::isTokenExpired).retry().atMost(1)
                        .subscribe()
                        .with(new Consumer<Response>() {
                            @Override
                            public void accept(Response response) {
                                if (response.getStatus() >= 200 || response.getStatus() < 300)
                                    logger.debug("File %s deleted from the Cloud Object Storage".formatted(outputPath));
                                else
                                    logger.error("Error during the execution of the delete operation for the file %s"
                                            .formatted(outputPath));
                            }
                        });
            }

            return extractedFile;

        } finally {

            if (removeUploadedFile) {
                cosClient.deleteFile(documentBucketName, uploadedPath)
                        .onFailure(WatsonxUtils::isTokenExpired).retry().atMost(1)
                        .subscribe()
                        .with(new Consumer<Response>() {
                            @Override
                            public void accept(Response response) {
                                if (response.getStatus() >= 200 || response.getStatus() < 300)
                                    logger.debug(
                                            "File %s deleted from the Cloud Object Storage".formatted(uploadedPath));
                                else
                                    logger.error("Error during the execution of the delete operation for the file %s"
                                            .formatted(uploadedPath));
                            }
                        });
            }
        }
    }

    /**
     * Options to configure the behavior of the @{link TextExtraction} methods.
     */
    public static class Options {

        Duration timeout;
        String outputFileName;
        List<String> languages;
        TextExtractionType type;
        boolean tableProcessing;
        Optional<Reference> documentReference;
        Optional<Reference> resultsReference;
        Optional<Boolean> removeUploadedFile;
        Optional<Boolean> removeOutputFile;

        protected Options(Duration timeout, String outputFileName, List<String> languages, TextExtractionType type,
                boolean tableProcessing, Optional<Reference> documentReference, Optional<Reference> resultsReference,
                Optional<Boolean> removeUploadedFile, Optional<Boolean> removeOutputFile) {
            this.timeout = timeout;
            this.outputFileName = outputFileName;
            this.languages = languages;
            this.type = type;
            this.tableProcessing = tableProcessing;
            this.documentReference = documentReference;
            this.resultsReference = resultsReference;
            this.removeUploadedFile = removeUploadedFile;
            this.removeOutputFile = removeOutputFile;
        }

        protected Options(Options options) {
            this(options.timeout, options.outputFileName, options.languages, options.type, options.tableProcessing,
                    options.documentReference, options.resultsReference, options.removeUploadedFile,
                    options.removeOutputFile);
        }

        public Options(List<Language> languages) {
            this.type = ASSEMBLY_MD;
            this.tableProcessing = true;
            this.removeUploadedFile = Optional.empty();
            this.removeOutputFile = Optional.empty();
            this.languages = convertList(languages);
            this.timeout = Duration.ofSeconds(60);
        }

        public static Options create(List<Language> languages) {
            return new Options(languages);
        }

        public Options outputFileName(String outputFileName) {
            this.outputFileName = outputFileName;
            return this;
        }

        public Options languages(List<Language> languages) {
            this.languages = convertList(languages);
            return this;
        }

        public Options type(TextExtractionType type) {
            this.type = type;
            return this;
        }

        public Options tableProcessing(boolean tableProcessing) {
            this.tableProcessing = tableProcessing;
            return this;
        }

        public Options timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Options documentReference(Reference documentReference) {
            this.documentReference = Optional.of(documentReference);
            return this;
        }

        public Options resultsReference(Reference resultsReference) {
            this.resultsReference = Optional.of(resultsReference);
            return this;
        }

        public Options removeUploadedFile(boolean removeUploadedFile) {
            this.removeUploadedFile = Optional.of(removeUploadedFile);
            return this;
        }

        public Options removeOutputFile(boolean removeOutputFile) {
            this.removeOutputFile = Optional.of(removeOutputFile);
            return this;
        }

        private List<String> convertList(List<Language> languages) {
            return isNull(languages) ? List.of() : languages.stream().map(Language::code).toList();
        }
    }

    public static enum Language {
        CHINESE_SIMPLIFIED("zh-CN"),
        CHINESE_TRADITIONAL("zh-TW"),
        DANISH("da"),
        DUTCH("nl"),
        ENGLISH("en"),
        ENGLISH_HANDWRITING("en_hw"),
        FINNISH("fi"),
        FRENCH("fr"),
        GERMAN("de"),
        GREEK("el"),
        HEBREW("he"),
        ITALIAN("it"),
        JAPANESE("ja"),
        KOREAN("ko"),
        NORWEGIAN_BOKMAL("nb"),
        NORWEGIAN_NYNORSK("nn"),
        POLISH("pl"),
        PORTUGUESE("pt"),
        SPANISH("es"),
        SWEDISH("sv");

        private final String code;

        Language(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}

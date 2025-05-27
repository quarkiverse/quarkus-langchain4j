package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;
import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;
import static io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Type.MD;
import static io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Type.PAGE_IMAGES;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.watsonx.WatsonxUtils;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.EmbeddedImages;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Mode;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.OCR;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionDataReference;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Type;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionResponse.ServiceError;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionResponse.Status;
import io.quarkiverse.langchain4j.watsonx.client.COSRestApi;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.exception.COSException;
import io.quarkiverse.langchain4j.watsonx.exception.TextExtractionException;

/**
 * This class provides methods for extracting text from high-value business documents, making them more accessible to AI models
 * or enabling the
 * identification of key information.
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
 * <li>MARKDOWN</li>
 * <li>HTML</li>
 * <li>PLAIN_TEXT</li>
 * <li>PAGE_IMAGES</li>
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
     * Constructs a {@code TextExtraction} instance with the required parameters to perform text extraction from documents
     * stored in IBM Cloud Object
     * Storage (COS). This constructor initializes the necessary references, project details, and client instances for
     * interacting with IBM COS and
     * Watsonx AI services.
     * <p>
     * <strong>Default Bucket:</strong>
     * <p>
     * The {@code documentReference.bucket} and {@code resultReference.bucket} fields are used as the default buckets for
     * uploading documents to COS and
     * they will serve as the target location for the following:
     * <ul>
     * <li>{@code documentReference.bucket}: The default bucket for uploading local documents to COS.</li>
     * <li>{@code resultReference.bucket}: The default bucket for uploading extracted documents to COS.</li>
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

        this.documentReference = documentReference;
        this.resultReference = resultReference;
        this.projectId = projectId;
        this.spaceId = spaceId;
        this.version = version;
        this.cosClient = cosClient;
        this.watsonxClient = watsonxClient;
    }

    /**
     * Starts the asynchronous text extraction process for a document stored in IBM Cloud Object Storage (COS). The extracted
     * text is saved as a new
     * <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md } extension. To customize the
     * output behavior, use the
     * method with the {@link Parameters} class.
     *
     * <pre>
     * {@code
     * String startExtraction(String absolutePath, Parameters parameters)
     * }
     * </pre>
     *
     * <b>Note:</b> This method does not return the extracted value. Use {@code extractAndFetch} to extract the text
     * immediately.
     *
     * @param absolutePath The COS path of the document to extract text from.
     * @return The unique identifier of the text extraction process.
     */
    public String startExtraction(String absolutePath) throws TextExtractionException {
        return startExtraction(absolutePath, Parameters.builder().build());
    }

    /**
     * Starts the asynchronous text extraction process for a document stored in IBM Cloud Object Storage (COS). The extracted
     * text is saved as a new
     * <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md} extension by default. Output
     * behavior can be customized
     * using the {@link Parameters} parameter.
     * <p>
     * <b>Note:</b> This method does not return the extracted text. Use {@code extractAndFetch} to extract the text immediately.
     *
     * @param absolutePath The COS path of the document to extract text from.
     * @param parameters The configuration parameters for text extraction.
     * @return The unique identifier of the text extraction process.
     */
    public String startExtraction(String absolutePath, Parameters parameters) throws TextExtractionException {
        requireNonNull(parameters);
        return startExtraction(absolutePath, parameters, false).metadata().id();
    }

    /**
     * Uploads a local file to IBM Cloud Object Storage (COS) and starts the asynchronous text extraction process. The extracted
     * text is saved as a new
     * <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md} extension by default. To
     * customize the output behavior you
     * can use the {@link Parameters} class.
     *
     * <pre>
     * {@code
     * String uploadAndStartExtraction(File file, Parameters parameters);
     * }
     * </pre>
     *
     * <b>Note:</b> This method does not return the extracted text. Use {@code uploadExtractAndFetch} to extract the text
     * immediately.
     *
     * @param file The local file to be uploaded and processed.
     * @return The unique identifier of the text extraction process.
     */
    public String uploadAndStartExtraction(File file) throws TextExtractionException {
        return uploadAndStartExtraction(file, Parameters.builder().build());
    }

    /**
     * Uploads a local file to IBM Cloud Object Storage (COS) and starts the asynchronous text extraction process. The extracted
     * text is saved as a new
     * <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md} extension by default. Output
     * behavior can be customized
     * using the {@link Parameters} parameter.
     *
     * <p>
     * <b>Note:</b> This method does not return the extracted text. Use {@code uploadExtractAndFetch} to extract the text
     * immediately.
     *
     * @param file The local file to be uploaded and processed.
     * @param parameters The configuration parameters for text extraction.
     * @return The unique identifier of the text extraction process.
     */
    public String uploadAndStartExtraction(File file, Parameters parameters) throws TextExtractionException {
        requireNonNull(parameters);
        requireNonNull(file);

        if (file.isDirectory())
            throw new TextExtractionException("directory_not_allowed", "The file can not be a directory");

        try {
            upload(new BufferedInputStream(new FileInputStream(file)), file.getName(), parameters, false);
            return startExtraction(file.getName(), parameters);
        } catch (FileNotFoundException e) {
            throw new TextExtractionException("file_not_found", e.getMessage(), e);
        }
    }

    /**
     * Uploads an InputStream to IBM Cloud Object Storage (COS) and starts the asynchronous text extraction process. The
     * extracted text is saved as a new
     * <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md} extension by default. To
     * customize the output behavior you
     * can use the {@link Parameters} class.
     *
     * <pre>
     * {@code
     * String uploadAndStartExtraction(InputStream is, String fileName, Parameters parameters);
     * }
     * </pre>
     *
     * <b>Note:</b> This method does not return the extracted text. Use {@code uploadExtractAndFetch} to extract the text
     * immediately.
     *
     * @param is The input stream of the file to be uploaded and processed.
     * @param fileName The name of the file to be uploaded and processed.
     * @return The unique identifier of the text extraction process.
     */
    public String uploadAndStartExtraction(InputStream is, String fileName)
            throws TextExtractionException {
        return uploadAndStartExtraction(is, fileName, Parameters.builder().build());
    }

    /**
     * Uploads an InputStream to IBM Cloud Object Storage (COS) and starts the asynchronous text extraction process. The
     * extracted text is saved as a new
     * <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md} extension by default. Output
     * behavior can be customized
     * using the {@link Parameters} class.
     *
     * <p>
     * <b>Note:</b> This method does not return the extracted text. Use {@code uploadExtractAndFetch} to extract the text
     * immediately.
     *
     * @param is The input stream of the file to be uploaded and processed.
     * @param fileName The name of the file to be uploaded and processed.
     * @param parameters The configuration parameters for text extraction.
     * @return The unique identifier of the text extraction process.
     */
    public String uploadAndStartExtraction(InputStream is, String fileName, Parameters parameters)
            throws TextExtractionException {
        requireNonNull(parameters);
        upload(is, fileName, parameters, false);
        return startExtraction(fileName, parameters);
    }

    /**
     * Starts the text extraction process for a file that is already present in Cloud Object Storage (COS) and returns the
     * extracted text value. The
     * extracted text is saved as a new <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md}
     * extension by default. To
     * customize the output behavior, use the method with the {@link Parameters} parameter.
     *
     * <pre>
     * {@code
     * String extractAndFetch(String absolutePath, Parameters parameters);
     * }
     * </pre>
     *
     * <b>Note:</b> The default timeout value is set to 60 seconds.
     *
     * @param absolutePath The absolute path of the file in Cloud Object Storage.
     * @return The text extracted.
     */
    public String extractAndFetch(String absolutePath) throws TextExtractionException {
        return extractAndFetch(absolutePath, Parameters.builder().build());
    }

    /**
     * Starts the text extraction process for a file that is already present in Cloud Object Storage (COS) and returns the
     * extracted text value. The
     * extracted text is saved as a new <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md}
     * extension by default.
     * Output behavior can be customized using the {@link Parameters} class.
     *
     * <p>
     * <li><b>Note:</b> This method waits until the extraction process is complete and returns the extracted value. The default
     * timeout value is set to 60
     * seconds.
     *
     * @param absolutePath The COS path of the document to extract text from.
     * @param parameters Configuration parameters, including cleanup behavior.
     * @return The text extracted.
     */
    public String extractAndFetch(String absolutePath, Parameters parameters) throws TextExtractionException {
        requireNonNull(parameters);
        if (parameters.types.size() > 1) {
            throw new TextExtractionException("fetch_operation_not_allowed",
                    "The fetch operation cannot be executed if more than one file is to be generated");
        }
        if (parameters.types.size() == 1 && parameters.types.get(0).equals(PAGE_IMAGES)) {
            throw new TextExtractionException("fetch_operation_not_allowed",
                    "The fetch operation cannot be executed for the type \"page_images\"");
        }
        var textExtractionResponse = startExtraction(absolutePath, parameters, true);
        return getExtractedText(textExtractionResponse, parameters);
    }

    /**
     * Uploads a local file to IBM Cloud Object Storage (COS), starts text extraction process and returns the extracted text
     * value. The extracted text is
     * saved as a new <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md} extension by
     * default. To customize the
     * output behavior, use the method with the {@link Parameters} class.
     *
     * <pre>
     * {@code
     * String uploadExtractAndFetch(File file, Parameters parameters);
     * }
     * </pre>
     *
     * <li><b>Note:</b> This method waits until the extraction process is complete and returns the extracted value. The default
     * timeout value is set to 60
     * seconds.
     *
     * @param file The local file to be uploaded and processed.
     * @return The text extracted.
     */
    public String uploadExtractAndFetch(File file) throws TextExtractionException {
        return uploadExtractAndFetch(file, Parameters.builder().build());
    }

    /**
     * Uploads a local file to IBM Cloud Object Storage (COS) and starts the asynchronous text extraction process. The extracted
     * text is saved as a new
     * <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md} extension by default. Output
     * behavior can be customized
     * using the {@link Parameters} class.
     *
     * <p>
     * <li><b>Notes:</b> This method waits until the extraction process is complete and returns the extracted value. The default
     * timeout value is set to
     * 60 seconds.
     *
     * @param file The local file to be uploaded and processed.
     * @param parameters Configuration parameters, including cleanup behavior.
     * @return The text extracted.
     */
    public String uploadExtractAndFetch(File file, Parameters parameters) throws TextExtractionException {
        requireNonNull(parameters);
        if (parameters.types.size() > 1) {
            throw new TextExtractionException("fetch_operation_not_allowed",
                    "The fetch operation cannot be executed if more than one file is to be generated");
        }
        if (parameters.types.size() == 1 && parameters.types.get(0).equals(PAGE_IMAGES)) {
            throw new TextExtractionException("fetch_operation_not_allowed",
                    "The fetch operation cannot be executed for the type \"page_images\"");
        }
        try {
            upload(new BufferedInputStream(new FileInputStream(file)), file.getName(), parameters, true);
        } catch (FileNotFoundException e) {
            throw new TextExtractionException("file_not_found", e.getMessage(), e);
        }
        return extractAndFetch(file.getName(), parameters);
    }

    /**
     * Uploads an InputStream to IBM Cloud Object Storage (COS), starts text extraction process and returns the extracted text
     * value. The extracted text
     * is saved as a new <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md} extension by
     * default. To customize the
     * output behavior, use the method with the {@link Parameters} class.
     *
     * <pre>
     * {@code
     * String uploadExtractAndFetch(InputStream is, String fileName, Parameters parameters);
     * }
     * </pre>
     *
     * <li><b>Note:</b> This method waits until the extraction process is complete and returns the extracted value. The default
     * timeout value is set to 60
     * seconds.
     *
     * @param is The input stream of the file to be uploaded and processed.
     * @param fileName The name of the file to be uploaded and processed.
     * @return The text extracted.
     */
    public String uploadExtractAndFetch(InputStream is, String fileName) throws TextExtractionException {
        return uploadExtractAndFetch(is, fileName, Parameters.builder().build());
    }

    /**
     * Uploads an InputStream to IBM Cloud Object Storage (COS) and starts the asynchronous text extraction process. The
     * extracted text is saved as a new
     * <b>Markdown</b> file in COS, preserving the original filename but using the {@code .md} extension by default. Output
     * behavior can be customized
     * using the {@link Parameters} class.
     *
     * <p>
     * <li><b>Note:</b> This method waits until the extraction process is complete and returns the extracted value. The default
     * timeout value is set to 60
     * seconds.
     *
     * @param is The input stream of the file to be uploaded and processed.
     * @param fileName The name of the file to be uploaded and processed.
     * @param parameters Configuration parameters, including cleanup behavior.
     * @return The text extracted.
     */
    public String uploadExtractAndFetch(InputStream is, String fileName, Parameters parameters)
            throws TextExtractionException {
        requireNonNull(parameters);
        if (parameters.types.size() > 1) {
            throw new TextExtractionException("fetch_operation_not_allowed",
                    "The fetch operation cannot be executed if more than one file is to be generated");
        }
        if (parameters.types.size() == 1 && parameters.types.get(0).equals(PAGE_IMAGES)) {
            throw new TextExtractionException("fetch_operation_not_allowed",
                    "The fetch operation cannot be executed for the type \"page_images\"");
        }
        upload(is, fileName, parameters, true);
        return extractAndFetch(fileName, parameters);
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
    private void upload(InputStream is, String fileName, Parameters parameters, boolean waitForExtraction) {
        requireNonNull(is);
        if (isNull(fileName) || fileName.isBlank())
            throw new IllegalArgumentException("The file name can not be null or empty");

        boolean removeOutputFile = parameters.removeOutputFile.orElse(false);
        boolean removeUploadedFile = parameters.removeUploadedFile.orElse(false);

        if (!waitForExtraction && (removeOutputFile || removeUploadedFile))
            throw new IllegalArgumentException(
                    "The asynchronous version of startExtraction doesn't allow the use of the \"removeOutputFile\" and \"removeUploadedFile\" parameters");

        Reference documentReference = firstOrDefault(this.documentReference, parameters.documentReference);
        retryOn(new Callable<Response>() {
            @Override
            public Response call() throws Exception {
                return cosClient.createFile(documentReference.bucket, fileName, is);
            }
        });
    }

    private TextExtractionResponse startExtraction(String absolutePath, Parameters parameters,
            boolean waitUntilJobIsDone)
            throws TextExtractionException {
        requireNonNull(absolutePath);
        requireNonNull(parameters);

        boolean removeOutputFile = parameters.removeOutputFile.orElse(false);
        boolean removeUploadedFile = parameters.removeUploadedFile.orElse(false);

        if (!waitUntilJobIsDone && (removeOutputFile || removeUploadedFile))
            throw new IllegalArgumentException(
                    "The asynchronous version of startExtraction doesn't allow the use of the \"removeOutputFile\" and \"removeUploadedFile\" parameters");

        if (isNull(parameters.outputFileName) || parameters.outputFileName.isBlank()) {

            if (parameters.types.size() > 1
                    || (parameters.types.size() == 1 && parameters.types.get(0).equals(PAGE_IMAGES))) {
                parameters.outputFileName = "/";
            } else if (parameters.types.size() == 1) {
                String extension = switch (parameters.types.get(0)) {
                    case JSON -> ".json";
                    case MD -> ".md";
                    case HTML -> ".html";
                    case PLAIN_TEXT -> ".txt";
                    case PAGE_IMAGES -> throw new RuntimeException(
                            "If you select \"page_images\" as type, the output file name cannot be null.");
                };
                var index = absolutePath.lastIndexOf(".");
                if (index > 0) {
                    parameters.outputFileName = absolutePath.substring(0, index) + extension;
                } else {
                    parameters.outputFileName = absolutePath + extension;
                }
            }
        }

        Reference documentReference = firstOrDefault(this.documentReference, parameters.documentReference);
        Reference resultsReference = firstOrDefault(this.resultReference, parameters.resultsReference);
        TextExtractionDataReference textExtractionDataReference = TextExtractionDataReference.of(documentReference.connection,
                absolutePath, documentReference.bucket);
        TextExtractionDataReference textExtractionResultsReference = TextExtractionDataReference.of(resultsReference.connection,
                parameters.outputFileName,
                resultsReference.bucket);
        TextExtractionParameters textExtractionParameters = new TextExtractionParameters(parameters.types, parameters.mode,
                parameters.ocr,
                parameters.autoRotationCorrection, parameters.embeddedImages, parameters.dpi,
                parameters.outputTokensAndBbox);

        var request = TextExtractionRequest.builder()
                .documentReference(textExtractionDataReference)
                .resultsReference(textExtractionResultsReference)
                .parameters(textExtractionParameters)
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
        LocalTime endTime = LocalTime.now().plus(parameters.timeout);

        do {

            if (LocalTime.now().isAfter(endTime))
                throw new TextExtractionException("timeout",
                        "Execution to extract %s file took longer than the timeout set by %s milliseconds"
                                .formatted(absolutePath, parameters.timeout.toMillis()));

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

    private String getExtractedText(TextExtractionResponse textExtractionResponse, Parameters parameters)
            throws TextExtractionException {
        requireNonNull(textExtractionResponse);
        requireNonNull(parameters);

        String uploadedPath = textExtractionResponse.entity().documentReference().location().fileName();
        String outputPath = textExtractionResponse.entity().resultsReference().location().fileName();
        Status status = textExtractionResponse.entity().results().status();
        boolean removeUploadedFile = parameters.removeUploadedFile.orElse(false);
        boolean removeOutputFile = parameters.removeOutputFile.orElse(false);

        Reference documentReference = firstOrDefault(this.documentReference, parameters.documentReference);
        String documentBucketName = documentReference.bucket;

        Reference resultsReference = firstOrDefault(this.resultReference, parameters.resultsReference);
        String resultsBucketName = resultsReference.bucket;

        try {

            String extractedFile = switch (status) {
                case COMPLETED -> retryOn(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return cosClient.getFileContent(resultsBucketName, parameters.outputFileName);
                    }
                });
                case FAILED -> {
                    ServiceError error = textExtractionResponse.entity().results().error();
                    throw new TextExtractionException(error.code(), error.message());
                }
                default -> throw new TextExtractionException("generic_error",
                        "Status %s not managed".formatted(status));
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
     * Parameters to configure the behavior of the @{link TextExtraction} methods.
     */
    public static class Parameters {

        final Duration timeout;
        String outputFileName;
        final List<Type> types;
        final Optional<Reference> documentReference;
        final Optional<Reference> resultsReference;
        final Optional<Boolean> removeUploadedFile;
        final Optional<Boolean> removeOutputFile;
        final Mode mode;
        final OCR ocr;
        final Boolean autoRotationCorrection;
        final EmbeddedImages embeddedImages;
        final Integer dpi;
        final Boolean outputTokensAndBbox;

        protected Parameters(Builder builder) {
            this.timeout = isNull(builder.timeout) ? Duration.ofSeconds(60) : builder.timeout;
            this.outputFileName = builder.outputFileName;
            this.types = isNull(builder.types) ? List.of(MD) : builder.types;
            this.documentReference = isNull(builder.documentReference) ? Optional.empty() : builder.documentReference;
            this.resultsReference = isNull(builder.resultsReference) ? Optional.empty() : builder.resultsReference;
            this.removeUploadedFile = isNull(builder.removeUploadedFile) ? Optional.empty() : builder.removeUploadedFile;
            this.removeOutputFile = isNull(builder.removeOutputFile) ? Optional.empty() : builder.removeOutputFile;
            this.mode = builder.mode;
            this.ocr = builder.ocr;
            this.autoRotationCorrection = builder.autoRotationCorrection;
            this.embeddedImages = builder.embeddedImages;
            this.dpi = builder.dpi;
            this.outputTokensAndBbox = builder.outputTokensAndBbox;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Duration getTimeout() {
            return timeout;
        }

        public String getOutputFileName() {
            return outputFileName;
        }

        public List<Type> getTypes() {
            return types;
        }

        public Optional<Reference> getDocumentReference() {
            return documentReference;
        }

        public Optional<Reference> getResultsReference() {
            return resultsReference;
        }

        public Optional<Boolean> getRemoveUploadedFile() {
            return removeUploadedFile;
        }

        public Optional<Boolean> getRemoveOutputFile() {
            return removeOutputFile;
        }

        public Mode getMode() {
            return mode;
        }

        public OCR getOcr() {
            return ocr;
        }

        public Boolean getAutoRotationCorrection() {
            return autoRotationCorrection;
        }

        public EmbeddedImages getEmbeddedImages() {
            return embeddedImages;
        }

        public Integer getDpi() {
            return dpi;
        }

        public Boolean getOutputTokensAndBbox() {
            return outputTokensAndBbox;
        }

        /**
         * Builder class for constructing {@link Parameters} used in a text extraction request.
         * <p>
         * This builder allows fine-grained configuration of options for text and metadata extraction, such as document
         * references, output settings, OCR
         * behavior, image options, and post-processing outputs.
         *
         * @see Parameters
         */
        public static class Builder {

            private Duration timeout;
            private String outputFileName;
            private List<Type> types;
            private Optional<Reference> documentReference;
            private Optional<Reference> resultsReference;
            private Optional<Boolean> removeUploadedFile;
            private Optional<Boolean> removeOutputFile;
            private Mode mode;
            private OCR ocr;
            private Boolean autoRotationCorrection;
            private EmbeddedImages embeddedImages;
            private Integer dpi;
            private Boolean outputTokensAndBbox;

            /**
             * Sets the maximum timeout for the extraction job.
             *
             * @param timeout a {@link Duration} representing how long the request may run.
             */
            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            /**
             * Sets the name of the output file.
             * <p>
             * If multiple files are expected, this must specify a directory name and end with a slash (e.g. {@code /results/}).
             *
             * @param outputFileName the name of the file or output directory
             */
            public Builder outputFileName(String outputFileName) {
                this.outputFileName = outputFileName;
                return this;
            }

            /**
             * Specifies the types of outputs to generate.
             *
             * @param types a list of {@link Type} indicating the processing types
             */
            public Builder types(List<Type> types) {
                this.types = types;
                return this;
            }

            /**
             * Sets a single output type to be generated.
             *
             * @param type a {@link Type} to specify the processing type
             */
            public Builder types(Type type) {
                this.types = List.of(type);
                return this;
            }

            /**
             * Sets multiple output types.
             *
             * @param types {@link Type} values
             */
            public Builder types(Type... types) {
                this.types = Arrays.asList(types);
                return this;
            }

            /**
             * Sets the reference to the input document.
             *
             * @param reference a {@link Reference} to the document to be processed
             */
            public Builder documentReference(Reference reference) {
                this.documentReference = Optional.ofNullable(reference);
                return this;
            }

            /**
             * Sets the reference for the results output.
             *
             * @param reference a {@link Reference} where extracted results will be stored
             */
            public Builder resultsReference(Reference reference) {
                this.resultsReference = Optional.ofNullable(reference);
                return this;
            }

            /**
             * Indicates whether the uploaded file should be deleted after processing.
             *
             * @param removeUploadedFile {@code true} to remove the uploaded file
             */
            public Builder removeUploadedFile(Boolean removeUploadedFile) {
                this.removeUploadedFile = Optional.ofNullable(removeUploadedFile);
                return this;
            }

            /**
             * Indicates whether the output file should be deleted after processing.
             *
             * @param removeOutputFile {@code true} to remove the output file
             */
            public Builder removeOutputFile(Boolean removeOutputFile) {
                this.removeOutputFile = Optional.ofNullable(removeOutputFile);
                return this;
            }

            /**
             * Sets the processing mode.
             *
             * @param mode a {@link Mode} value specifying the processing mode
             */
            public Builder mode(Mode mode) {
                this.mode = mode;
                return this;
            }

            /**
             * Sets the OCR mode used for document processing.
             *
             * @param ocr an {@link OCR} configuration
             */
            public Builder ocr(OCR ocr) {
                this.ocr = ocr;
                return this;
            }

            /**
             * Enables or disables automatic rotation correction for input pages.
             * <p>
             * When enabled, the service attempts to detect and fix page orientation. Default is {@code false}.
             *
             * @param autoRotationCorrection true to enable auto-rotation correction
             */
            public Builder autoRotationCorrection(Boolean autoRotationCorrection) {
                this.autoRotationCorrection = autoRotationCorrection;
                return this;
            }

            /**
             * Specifies the behavior for embedded image generation.
             *
             * @param embeddedImages option for embedded image generation
             */
            public Builder embeddedImages(EmbeddedImages embeddedImages) {
                this.embeddedImages = embeddedImages;
                return this;
            }

            /**
             * Sets the target DPI for output images.
             *
             * @return this builder instance
             */
            public Builder dpi(Integer dpi) {
                this.dpi = dpi;
                return this;
            }

            /**
             * Specifies whether to return individual tokens and bounding boxes in the response.
             * <p>
             * When {@code false}, token-level structures are excluded.
             *
             * @param outputTokensAndBbox true to include tokens and bounding boxes
             */
            public Builder outputTokensAndBbox(Boolean outputTokensAndBbox) {
                this.outputTokensAndBbox = outputTokensAndBbox;
                return this;
            }

            /**
             * Builds and returns a {@link Parameters} object with the configured values.
             *
             * @return a new {@link Parameters} instance
             */
            public Parameters build() {
                return new Parameters(this);
            }
        }
    }
}

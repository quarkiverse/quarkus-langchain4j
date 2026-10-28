package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Text extraction is not modelled by the watsonx dialect: raw stubs serve the whole job flow, including a job that is
 * still running when it is first polled and the result file in Cloud Object Storage.
 */
public class WatsonxRawStubTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.watsonx.text-extraction.cos-url=${quarkus.langchain4j.mock-api.url}/cos
                            quarkus.langchain4j.watsonx.text-extraction.document-reference.connection=doc-connection
                            quarkus.langchain4j.watsonx.text-extraction.document-reference.bucket-name=documents
                            quarkus.langchain4j.watsonx.text-extraction.results-reference.connection=result-connection
                            quarkus.langchain4j.watsonx.text-extraction.results-reference.bucket-name=results
                            """), "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @Inject
    TextExtractionService textExtraction;

    @Inject
    MockModelServer mock;

    @Test
    void extractionJobIsPolledUntilDone() throws Exception {
        mock.reset();
        mock.raw().on("POST", "/watsonx/ml/v1/text/extractions").reply(201, job("submitted"));
        mock.raw().on("GET", "/watsonx/ml/v1/text/extractions/extraction-1")
                .replies(RawResponse.json(200, job("running")), RawResponse.json(200, job("completed")));
        mock.raw().on("GET", "/cos/results/document.md")
                .replies(RawResponse.of(200, Map.of("Content-Type", "text/plain"), "# Extracted text"));

        assertThat(textExtraction.extractAndFetch("document.pdf")).isEqualTo("# Extracted text");

        List<CapturedRequest> requests = mock.requests().stream()
                .filter(r -> !r.path().equals("/watsonx/identity/token"))
                .toList();
        assertThat(requests).extracting(CapturedRequest::path).containsExactly(
                "/watsonx/ml/v1/text/extractions",
                "/watsonx/ml/v1/text/extractions/extraction-1",
                "/watsonx/ml/v1/text/extractions/extraction-1",
                "/cos/results/document.md");
        assertThat(requests.get(0).bodyAsJson().at("/document_reference/location/file_name").asText())
                .isEqualTo("document.pdf");
        assertThat(requests.get(0).bodyAsJson().at("/project_id").asText()).isEqualTo("mock-project");
        assertThat(requests).allSatisfy(r -> assertThat(r.header("Authorization")).isEqualTo("Bearer mock-iam-token"));
    }

    /**
     * A {@code TextExtractionResponse} as in the watsonx.ai API reference.
     */
    private static String job(String status) {
        return """
                {"metadata":{"id":"extraction-1","created_at":"2026-10-19T10:00:00Z","project_id":"mock-project"},
                 "entity":{
                   "document_reference":{"type":"connection_asset","connection":{"id":"doc-connection"},
                                         "location":{"file_name":"document.pdf","bucket":"documents"}},
                   "results_reference":{"type":"connection_asset","connection":{"id":"result-connection"},
                                        "location":{"file_name":"document.md","bucket":"results"}},
                   "results":{"status":"%s","number_pages_processed":1,"running_at":"2026-10-19T10:00:01Z"}}}
                """.formatted(status);
    }
}

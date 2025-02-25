package io.quarkiverse.langchain4j.sample;

import static java.nio.file.Path.of;
import dev.langchain4j.data.pdf.PdfFile;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

@Path("/analyze-pdf-document")
public class PdfDocumentAnalyzerResource {

    private final PdfDocumentAnalyzer analyzer;

    public PdfDocumentAnalyzerResource(PdfDocumentAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @GET
    public RentalReport analyzePdfDocument() throws Exception {
        return analyzer.analyze(PdfFile.builder().base64Data(getPdfDocumentContent()).build());
    }

    private static String getPdfDocumentContent() throws IOException {
        java.nio.file.Path pdfDocumentPath = of("src/main/resources/rental.pdf");
        return Base64.getEncoder().encodeToString(Files.readAllBytes(pdfDocumentPath));
    }

}

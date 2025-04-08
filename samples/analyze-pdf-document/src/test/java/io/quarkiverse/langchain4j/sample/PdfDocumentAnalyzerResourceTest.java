package io.quarkiverse.langchain4j.sample;


import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import dev.langchain4j.data.pdf.PdfFile;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class PdfDocumentAnalyzerResourceTest {

    @InjectMock
    PdfDocumentAnalyzer analyzer;

    @Test
    void test() {
        Mockito.when(analyzer.analyze(any(PdfFile.class))).thenReturn(new RentalReport(LocalDate.now(), LocalDate.now(), LocalDate.now(), "foo", "bar", BigDecimal.ZERO));
        when().get("/analyze-pdf-document")
                .then()
                .statusCode(200)
                .body("landlordName", is("foo"));
    }
}

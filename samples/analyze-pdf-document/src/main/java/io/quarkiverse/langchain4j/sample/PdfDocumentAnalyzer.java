package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.PdfUrl;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface PdfDocumentAnalyzer {

    @UserMessage("Analyze the given document")
    RentalReport analyze(@PdfUrl PdfFile pdfFile);
}

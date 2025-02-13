package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.output.JsonSchemas;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static java.nio.file.Path.of;

@Path("/analyze-pdf-document")
public class PdfDocumentAnalyzer {

    private final ChatLanguageModel model;

    public PdfDocumentAnalyzer(ChatLanguageModel model) {
        this.model = model;
    }

    @GET
    public String analyzePdfDocument() throws Exception {

        UserMessage userMessage = UserMessage.from(
                TextContent.from("Analyze the given document"),
                PdfFileContent.from(getPdfDocumentContent(), "application/pdf")
        );

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .responseFormat(responseFormatFrom(RentalReport.class))
                        .build())
                .build();

        ChatResponse chatResponse = model.chat(chatRequest);

        return chatResponse.aiMessage().text();
    }

    private static String getPdfDocumentContent() throws IOException {
        java.nio.file.Path pdfDocumentPath = of("src/main/resources/rental.pdf");
        return Base64.getEncoder().encodeToString(Files.readAllBytes(pdfDocumentPath));
    }

    private static ResponseFormat responseFormatFrom(Class<?> clazz) {
        return ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchemas.jsonSchemaFrom(clazz).get())
                .build();
    }
}

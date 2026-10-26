package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.pdf.PdfFile;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(PdfFileContent.class)
public abstract class PdfFileContentMixin {

    @JsonCreator
    public PdfFileContentMixin(@JsonProperty("pdfFile") PdfFile pdfFile) {
    }
}

package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.langchain4j.data.pdf.PdfFile;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(PdfFile.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class PdfFileBuilderMixin {
}

package io.quarkiverse.langchain4j.runtime.jackson;

import dev.langchain4j.data.pdf.PdfFile;
import io.quarkus.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

@JacksonMixin(PdfFile.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class PdfFileBuilderMixin {
}

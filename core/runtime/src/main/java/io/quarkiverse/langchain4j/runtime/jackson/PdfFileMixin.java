package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.data.pdf.PdfFile;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(PdfFile.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = PdfFile.Builder.class)
public abstract class PdfFileMixin {

}

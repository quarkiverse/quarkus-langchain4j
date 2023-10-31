package io.quarkiverse.langchain4j.huggingface.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(TextGenerationResponse.class)
public abstract class TextGenerationResponseMixin {

    @JsonCreator
    public TextGenerationResponseMixin(@JsonProperty("generated_text") String generatedText) {

    }
}

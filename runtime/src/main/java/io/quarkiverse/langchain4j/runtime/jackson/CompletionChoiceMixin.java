package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.completion.CompletionChoice;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(CompletionChoice.class)
@JsonDeserialize(builder = CompletionChoice.Builder.class)
public abstract class CompletionChoiceMixin {
}

package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.moderation.CategoryScores;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(CategoryScores.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class CategoryScoresBuilderMixin {
}

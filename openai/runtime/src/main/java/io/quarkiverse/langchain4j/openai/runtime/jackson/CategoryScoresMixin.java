package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.moderation.CategoryScores;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(CategoryScores.class)
@JsonDeserialize(builder = CategoryScores.Builder.class)
public abstract class CategoryScoresMixin {
}

package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.moderation.Categories;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Categories.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class CategoriesBuilderMixin {
}

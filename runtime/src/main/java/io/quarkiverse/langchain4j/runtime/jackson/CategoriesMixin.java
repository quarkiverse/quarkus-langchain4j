package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.moderation.Categories;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Categories.class)
@JsonDeserialize(builder = Categories.Builder.class)
public abstract class CategoriesMixin {
}

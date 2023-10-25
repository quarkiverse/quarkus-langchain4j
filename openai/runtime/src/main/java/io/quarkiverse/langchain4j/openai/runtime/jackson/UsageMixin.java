package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.shared.Usage;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Usage.class)
@JsonDeserialize(builder = Usage.Builder.class)
public abstract class UsageMixin {
}

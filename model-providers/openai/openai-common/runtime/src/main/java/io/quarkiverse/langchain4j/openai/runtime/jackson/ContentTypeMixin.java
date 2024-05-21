package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import dev.ai4j.openai4j.chat.ContentType;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ContentType.class)
@JsonSerialize(using = ContentTypeSerializer.class)
public abstract class ContentTypeMixin {
}

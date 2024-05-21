package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import dev.ai4j.openai4j.chat.Content;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Content.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class ContentMixin {
}

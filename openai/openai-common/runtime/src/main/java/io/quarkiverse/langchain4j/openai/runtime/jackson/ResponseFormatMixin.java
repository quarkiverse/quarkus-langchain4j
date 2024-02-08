package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import dev.ai4j.openai4j.chat.ResponseFormat;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ResponseFormat.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class ResponseFormatMixin {
}

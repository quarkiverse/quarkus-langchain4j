package io.quarkiverse.langchain4j.openai.runtime.jackson;

import dev.ai4j.openai4j.chat.ResponseFormatType;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ResponseFormatType.class)
public abstract class ResponseFormatTypeMixin {
}

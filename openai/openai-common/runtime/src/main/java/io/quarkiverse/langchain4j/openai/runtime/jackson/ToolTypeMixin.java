package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import dev.ai4j.openai4j.chat.ToolType;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ToolType.class)
@JsonSerialize(using = ToolTypeSerializer.class)
@JsonDeserialize(using = ToolTypeDeserializer.class)
public abstract class ToolTypeMixin {

}

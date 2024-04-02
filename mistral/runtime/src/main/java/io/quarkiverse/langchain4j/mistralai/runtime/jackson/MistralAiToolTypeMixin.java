package io.quarkiverse.langchain4j.mistralai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import dev.langchain4j.model.mistralai.MistralAiToolType;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(MistralAiToolType.class)
@JsonSerialize(using = ToolTypeSerializer.class)
public abstract class MistralAiToolTypeMixin {

}

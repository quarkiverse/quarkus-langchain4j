package io.quarkiverse.langchain4j.mistralai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import dev.langchain4j.model.mistralai.MistralAiToolChoiceName;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(MistralAiToolChoiceName.class)
@JsonSerialize(using = ToolChoiceNameSerializer.class)
public abstract class MistralAiToolChoiceMixin {

}

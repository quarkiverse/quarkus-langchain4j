package io.quarkiverse.langchain4j.mistralai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import dev.langchain4j.model.mistralai.MistralAiRole;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(MistralAiRole.class)
@JsonSerialize(using = RoleSerializer.class)
@JsonDeserialize(using = RoleDeserializer.class)
public abstract class MistralAiRoleMixin {

}

package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.data.message.AiMessage;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(AiMessage.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(builder = AiMessage.Builder.class)
public abstract class AiMessageMixin {

}

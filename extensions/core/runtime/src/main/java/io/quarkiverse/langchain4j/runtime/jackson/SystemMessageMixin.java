package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.message.SystemMessage;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(SystemMessage.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class SystemMessageMixin {

    @JsonCreator
    public SystemMessageMixin(@JsonProperty("text") String text) {

    }

}

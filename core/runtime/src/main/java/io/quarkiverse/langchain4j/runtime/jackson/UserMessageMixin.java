package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.message.UserMessage;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(UserMessage.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class UserMessageMixin {

    @JsonCreator
    public UserMessageMixin(@JsonProperty("name") String name, @JsonProperty("text") String text) {

    }

}

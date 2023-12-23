package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ToolExecutionResultMessage.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "text", "id", "toolName", "type" }) // needed for tests
public class ToolExecutionResultMessageMixin {

    @JsonCreator
    public ToolExecutionResultMessageMixin(@JsonProperty("id") String id, @JsonProperty("toolName") String toolName,
            @JsonProperty("toolExecutionResult") String toolExecutionResult) {

    }
}

package io.quarkiverse.langchain4j.runtime.jackson;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(AiMessage.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(using = AiMessageDeserializer.class)
public abstract class AiMessageMixin {

    @JsonCreator
    public AiMessageMixin(@JsonProperty("toolExecutionRequests") List<ToolExecutionRequest> toolExecutionRequests) {

    }

}

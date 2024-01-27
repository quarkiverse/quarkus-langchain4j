package io.quarkiverse.langchain4j.huggingface.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import dev.langchain4j.model.huggingface.client.Options;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Options.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class OptionsMixin {
}

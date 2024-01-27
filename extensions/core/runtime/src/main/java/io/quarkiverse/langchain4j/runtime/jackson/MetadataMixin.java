package io.quarkiverse.langchain4j.runtime.jackson;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.document.Metadata;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Metadata.class)
public abstract class MetadataMixin {

    @JsonCreator
    public MetadataMixin(@JsonProperty("metadata") Map<String, String> metadata) {

    }
}

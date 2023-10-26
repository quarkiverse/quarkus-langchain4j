package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(TextSegment.class)
public abstract class TextSegmentMixin {

    @JsonCreator
    public TextSegmentMixin(@JsonProperty("text") String text, @JsonProperty("metadata") Metadata metadata) {

    }
}

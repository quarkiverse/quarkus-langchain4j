package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.data.message.TextContent;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(TextContent.class)
@JsonDeserialize(using = TextContentDeserializer.class)
public abstract class TextContentMixin {

}

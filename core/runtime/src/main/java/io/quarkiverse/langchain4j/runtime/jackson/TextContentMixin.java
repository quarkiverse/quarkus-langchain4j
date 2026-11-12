package io.quarkiverse.langchain4j.runtime.jackson;

import dev.langchain4j.data.message.TextContent;
import io.quarkus.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonDeserialize;

@JacksonMixin(TextContent.class)
@JsonDeserialize(using = TextContentDeserializer.class)
public abstract class TextContentMixin {

}

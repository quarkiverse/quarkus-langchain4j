package io.quarkiverse.langchain4j.runtime.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import dev.langchain4j.data.message.TextContent;

public class TextContentDeserializer extends StdDeserializer<TextContent> {

    public TextContentDeserializer() {
        super(TextContent.class);
    }

    @Override
    public TextContent deserialize(JsonParser p, DeserializationContext deserializationContext)
            throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        return new TextContent(node.get("text").asText());
    }
}

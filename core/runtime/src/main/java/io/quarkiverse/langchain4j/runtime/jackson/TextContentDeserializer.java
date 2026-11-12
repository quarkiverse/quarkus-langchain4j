package io.quarkiverse.langchain4j.runtime.jackson;

import dev.langchain4j.data.message.TextContent;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class TextContentDeserializer extends StdDeserializer<TextContent> {

    public TextContentDeserializer() {
        super(TextContent.class);
    }

    @Override
    public TextContent deserialize(JsonParser p, DeserializationContext deserializationContext) {
        JsonNode node = p.readValueAsTree();
        return new TextContent(node.get("text").asText());
    }
}

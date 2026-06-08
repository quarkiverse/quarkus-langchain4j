package io.quarkiverse.langchain4j.agentic.runtime.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

/**
 * Mirror of {@link AgenticScopeJsonSerializer}: reconstructs a {@link DefaultAgenticScope} from JSON by
 * delegating to langchain4j's {@link AgenticScopeSerializer}.
 */
public class AgenticScopeJsonDeserializer extends JsonDeserializer<DefaultAgenticScope> {

    @Override
    public DefaultAgenticScope deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        return AgenticScopeSerializer.fromJson(node.toString());
    }
}

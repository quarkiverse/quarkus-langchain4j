package io.quarkiverse.langchain4j.agentic.runtime.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

/**
 * Serializes an {@link AgenticScope} by delegating to langchain4j's own {@link AgenticScopeSerializer}.
 * <p>
 * A vanilla Jackson {@code ObjectMapper} cannot serialize {@link DefaultAgenticScope}: its state is held
 * in private fields with no discoverable bean properties (plus {@code transient} fields such as a lock),
 * so serialization fails with {@code InvalidDefinitionException}. langchain4j ships a dedicated codec with
 * the required mixins and type information; this serializer routes through it so the managed ObjectMapper
 * produces the same correct JSON.
 */
public class AgenticScopeJsonSerializer extends JsonSerializer<AgenticScope> {

    @Override
    public void serialize(AgenticScope value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (!(value instanceof DefaultAgenticScope defaultScope)) {
            throw new IOException("Unsupported AgenticScope implementation: " + value.getClass().getName());
        }
        gen.writeRawValue(AgenticScopeSerializer.toJson(defaultScope));
    }
}

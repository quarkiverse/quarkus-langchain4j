package io.quarkiverse.langchain4j.agentic.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

/**
 * Guards the serialization contract that downstream persistence integrations (e.g. quarkus-flow) rely on:
 * a {@link DefaultAgenticScope} cannot be marshalled with a vanilla Jackson {@link ObjectMapper}, it must
 * go through langchain4j's {@link AgenticScopeSerializer}, which configures the required mixins and typing.
 *
 * <p>
 * Marshalling with a raw ObjectMapper fails because {@code DefaultAgenticScope} exposes no
 * Jackson-discoverable properties.
 */
class AgenticScopeSerializationTest {

    private DefaultAgenticScope newScope() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("topic", "dragons and wizards");
        scope.writeState("style", "fantasy");
        scope.writeState("score", 0.9);
        return scope;
    }

    @Test
    void rawObjectMapperCannotSerializeAgenticScope() {
        assertThrows(JsonMappingException.class,
                () -> new ObjectMapper().writeValueAsString(newScope()));
    }

    @Test
    void agenticScopeSerializerRoundTripsState() throws Exception {
        DefaultAgenticScope scope = newScope();

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(json);

        assertEquals("dragons and wizards", restored.readState("topic"));
        assertEquals("fantasy", restored.readState("style"));
        assertEquals(0.9, restored.readState("score", 0.0));
    }
}

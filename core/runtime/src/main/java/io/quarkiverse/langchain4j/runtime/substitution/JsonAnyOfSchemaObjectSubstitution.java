package io.quarkiverse.langchain4j.runtime.substitution;

import java.util.List;

import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class JsonAnyOfSchemaObjectSubstitution
        implements ObjectSubstitution<JsonAnyOfSchema, JsonAnyOfSchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonAnyOfSchema obj) {
        return new Serialized(obj.description(), obj.anyOf());
    }

    @Override
    public JsonAnyOfSchema deserialize(Serialized obj) {
        return JsonAnyOfSchema.builder()
                .description(obj.description)
                .anyOf(obj.anyOf)
                .build();
    }

    public record Serialized(String description, List<JsonSchemaElement> anyOf) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

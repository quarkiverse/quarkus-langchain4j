package io.quarkiverse.langchain4j.runtime.substitution;

import java.util.List;

import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class JsonEnumSchemaObjectSubstitution
        implements ObjectSubstitution<JsonEnumSchema, JsonEnumSchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonEnumSchema obj) {
        return new Serialized(obj.description(), obj.enumValues());
    }

    @Override
    public JsonEnumSchema deserialize(Serialized obj) {
        return JsonEnumSchema.builder()
                .description(obj.description)
                .enumValues(obj.enumValues)
                .build();
    }

    public record Serialized(String description, List<String> enumValues) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

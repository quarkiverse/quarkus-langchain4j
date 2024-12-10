package io.quarkiverse.langchain4j.runtime.substitution;

import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class JsonBooleanSchemaObjectSubstitution
        implements ObjectSubstitution<JsonBooleanSchema, JsonBooleanSchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonBooleanSchema obj) {
        return new Serialized(obj.description());
    }

    @Override
    public JsonBooleanSchema deserialize(Serialized obj) {
        return JsonBooleanSchema.builder()
                .description(obj.description)
                .build();
    }

    public record Serialized(String description) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

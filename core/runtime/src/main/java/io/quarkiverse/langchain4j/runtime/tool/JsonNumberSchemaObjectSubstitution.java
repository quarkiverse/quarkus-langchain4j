package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class JsonNumberSchemaObjectSubstitution
        implements ObjectSubstitution<JsonNumberSchema, JsonNumberSchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonNumberSchema obj) {
        return new Serialized(obj.description());
    }

    @Override
    public JsonNumberSchema deserialize(Serialized obj) {
        return JsonNumberSchema.builder()
                .description(obj.description)
                .build();
    }

    public record Serialized(String description) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

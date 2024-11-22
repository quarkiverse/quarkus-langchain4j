package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public final class JsonIntegerSchemaObjectSubstitution
        implements ObjectSubstitution<JsonIntegerSchema, JsonIntegerSchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonIntegerSchema obj) {
        return new Serialized(obj.description());
    }

    @Override
    public JsonIntegerSchema deserialize(Serialized obj) {
        return JsonIntegerSchema.builder()
                .description(obj.description)
                .build();
    }

    public record Serialized(String description) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

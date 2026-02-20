package io.quarkiverse.langchain4j.runtime.substitution;

import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class JsonRawSchemaObjectSubstitution
        implements ObjectSubstitution<JsonRawSchema, JsonRawSchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonRawSchema obj) {
        return new Serialized(obj.schema());
    }

    @Override
    public JsonRawSchema deserialize(Serialized obj) {
        return JsonRawSchema.builder()
                .schema(obj.schema)
                .build();
    }

    public record Serialized(String schema) {
        @RecordableConstructor
        public Serialized {
        }
    }
}
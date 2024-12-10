package io.quarkiverse.langchain4j.runtime.substitution;

import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class JsonReferenceSchemaObjectSubstitution
        implements ObjectSubstitution<JsonReferenceSchema, JsonReferenceSchemaObjectSubstitution.Serialized> {
    public Serialized serialize(JsonReferenceSchema obj) {
        return new Serialized(obj.reference());
    }

    public JsonReferenceSchema deserialize(Serialized obj) {
        return JsonReferenceSchema.builder()
                .reference(obj.reference)
                .build();
    }

    public record Serialized(String reference) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

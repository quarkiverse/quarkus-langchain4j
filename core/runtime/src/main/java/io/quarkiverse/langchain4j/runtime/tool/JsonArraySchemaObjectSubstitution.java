package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class JsonArraySchemaObjectSubstitution
        implements ObjectSubstitution<JsonArraySchema, JsonArraySchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonArraySchema obj) {
        return new Serialized(obj.description(), obj.items());
    }

    @Override
    public JsonArraySchema deserialize(Serialized obj) {
        return JsonArraySchema.builder()
                .description(obj.description)
                .items(obj.items)
                .build();
    }

    public record Serialized(String description, JsonSchemaElement items) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

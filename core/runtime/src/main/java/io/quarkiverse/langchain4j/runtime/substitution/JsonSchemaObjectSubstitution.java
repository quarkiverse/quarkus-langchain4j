package io.quarkiverse.langchain4j.runtime.substitution;

import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class JsonSchemaObjectSubstitution
        implements ObjectSubstitution<JsonSchema, JsonSchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonSchema obj) {
        return new Serialized(obj.name(), obj.rootElement());
    }

    @Override
    public JsonSchema deserialize(Serialized obj) {
        return JsonSchema.builder()
                .name(obj.name)
                .rootElement(obj.rootElement)
                .build();
    }

    public record Serialized(String name, JsonSchemaElement rootElement) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

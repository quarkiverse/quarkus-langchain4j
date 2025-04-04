package io.quarkiverse.langchain4j.runtime.substitution;

import java.util.List;
import java.util.Map;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class JsonObjectSchemaObjectSubstitution
        implements ObjectSubstitution<JsonObjectSchema, JsonObjectSchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonObjectSchema obj) {
        return new Serialized(obj.description(), obj.properties(), obj.required(), obj.additionalProperties(),
                obj.definitions());
    }

    @Override
    public JsonObjectSchema deserialize(Serialized obj) {
        return JsonObjectSchema.builder()
                .description(obj.description)
                .addProperties(obj.properties)
                .required(obj.required)
                .additionalProperties(obj.additionalProperties)
                .definitions(obj.definitions)
                .build();
    }

    public record Serialized(String description, Map<String, JsonSchemaElement> properties, List<String> required,
            Boolean additionalProperties, Map<String, JsonSchemaElement> definitions) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

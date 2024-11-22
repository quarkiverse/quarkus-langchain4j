package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public final class JsonStringSchemaObjectSubstitution
        implements ObjectSubstitution<JsonStringSchema, JsonStringSchemaObjectSubstitution.Serialized> {
    @Override
    public Serialized serialize(JsonStringSchema obj) {
        return new Serialized(obj.description());
    }

    @Override
    public JsonStringSchema deserialize(Serialized obj) {
        return JsonStringSchema.builder()
                .description(obj.description)
                .build();
    }

    public record Serialized(String description) {
        @RecordableConstructor
        public Serialized {
        }
    }
}

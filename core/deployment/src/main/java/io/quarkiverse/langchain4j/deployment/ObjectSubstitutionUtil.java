package io.quarkiverse.langchain4j.deployment;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.quarkiverse.langchain4j.runtime.substitution.JsonArraySchemaObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.substitution.JsonBooleanSchemaObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.substitution.JsonEnumSchemaObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.substitution.JsonIntegerSchemaObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.substitution.JsonNumberSchemaObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.substitution.JsonObjectSchemaObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.substitution.JsonRawSchemaObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.substitution.JsonReferenceSchemaObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.substitution.JsonSchemaObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.substitution.JsonStringSchemaObjectSubstitution;
import io.quarkus.deployment.recording.RecorderContext;

final class ObjectSubstitutionUtil {

    private ObjectSubstitutionUtil() {
    }

    static void registerJsonSchema(RecorderContext recorderContext) {
        recorderContext.registerSubstitution(JsonSchema.class, JsonSchemaObjectSubstitution.Serialized.class,
                JsonSchemaObjectSubstitution.class);
        recorderContext.registerSubstitution(JsonArraySchema.class, JsonArraySchemaObjectSubstitution.Serialized.class,
                JsonArraySchemaObjectSubstitution.class);
        recorderContext.registerSubstitution(JsonBooleanSchema.class, JsonBooleanSchemaObjectSubstitution.Serialized.class,
                JsonBooleanSchemaObjectSubstitution.class);
        recorderContext.registerSubstitution(JsonEnumSchema.class, JsonEnumSchemaObjectSubstitution.Serialized.class,
                JsonEnumSchemaObjectSubstitution.class);
        recorderContext.registerSubstitution(JsonIntegerSchema.class, JsonIntegerSchemaObjectSubstitution.Serialized.class,
                JsonIntegerSchemaObjectSubstitution.class);
        recorderContext.registerSubstitution(JsonNumberSchema.class, JsonNumberSchemaObjectSubstitution.Serialized.class,
                JsonNumberSchemaObjectSubstitution.class);
        recorderContext.registerSubstitution(JsonObjectSchema.class, JsonObjectSchemaObjectSubstitution.Serialized.class,
                JsonObjectSchemaObjectSubstitution.class);
        recorderContext.registerSubstitution(JsonRawSchema.class, JsonRawSchemaObjectSubstitution.Serialized.class,
                JsonRawSchemaObjectSubstitution.class);
        recorderContext.registerSubstitution(JsonReferenceSchema.class,
                JsonReferenceSchemaObjectSubstitution.Serialized.class,
                JsonReferenceSchemaObjectSubstitution.class);
        recorderContext.registerSubstitution(JsonStringSchema.class, JsonStringSchemaObjectSubstitution.Serialized.class,
                JsonStringSchemaObjectSubstitution.class);
    }
}

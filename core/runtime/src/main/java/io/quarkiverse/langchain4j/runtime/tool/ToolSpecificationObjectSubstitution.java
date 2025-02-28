package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class ToolSpecificationObjectSubstitution
        implements ObjectSubstitution<ToolSpecification, ToolSpecificationObjectSubstitution.Serialized> {

    @Override
    public Serialized serialize(ToolSpecification obj) {
        return new Serialized(obj.name(), obj.description(), obj.parameters());
    }

    @Override
    public ToolSpecification deserialize(Serialized obj) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(obj.name)
                .description(obj.description);
        if (obj.parameters != null) {
            builder.parameters(obj.parameters);
        }
        return builder.build();
    }

    public static class Serialized {
        private final String name;
        private final String description;
        private final JsonObjectSchema parameters;

        @RecordableConstructor
        public Serialized(String name, String description,
                JsonObjectSchema parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public JsonObjectSchema getParameters() {
            return parameters;
        }

    }
}

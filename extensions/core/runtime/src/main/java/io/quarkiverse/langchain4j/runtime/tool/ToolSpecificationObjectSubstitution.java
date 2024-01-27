package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
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
        return ToolSpecification.builder()
                .name(obj.name)
                .description(obj.description)
                .parameters(obj.parameters).build();
    }

    public static class Serialized {
        private final String name;
        private final String description;
        private final ToolParameters parameters;

        @RecordableConstructor
        public Serialized(String name, String description, ToolParameters parameters) {
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

        public ToolParameters getParameters() {
            return parameters;
        }

    }
}

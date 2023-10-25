package io.quarkiverse.langchain4j.runtime.tool;

import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolParameters;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class ToolParametersObjectSubstitution
        implements ObjectSubstitution<ToolParameters, ToolParametersObjectSubstitution.Serialized> {

    @Override
    public Serialized serialize(ToolParameters obj) {
        return new Serialized(obj.type(), obj.properties(), obj.required());
    }

    @Override
    public ToolParameters deserialize(Serialized obj) {
        return ToolParameters.builder()
                .type(obj.type)
                .required(obj.required)
                .properties(obj.properties).build();
    }

    public static class Serialized {
        private final String type;
        private final Map<String, Map<String, Object>> properties;
        private final List<String> required;

        @RecordableConstructor
        public Serialized(String type, Map<String, Map<String, Object>> properties, List<String> required) {
            this.type = type;
            this.properties = properties;
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public Map<String, Map<String, Object>> getProperties() {
            return properties;
        }

        public List<String> getRequired() {
            return required;
        }
    }
}

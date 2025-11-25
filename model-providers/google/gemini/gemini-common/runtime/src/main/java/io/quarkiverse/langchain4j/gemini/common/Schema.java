package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Schema {
    private final Type type;
    private final String format;
    private final String description;
    private final Boolean nullable;
    @JsonProperty("enum")
    private final List<String> enumeration;
    private final String maxItems;
    private final Map<String, Schema> properties;
    private final List<String> required;
    private final Schema items;

    public Schema(Builder builder) {
        this.type = builder.type;
        this.format = builder.format;
        this.description = builder.description;
        this.nullable = builder.nullable;
        this.enumeration = builder.enumeration;
        this.maxItems = builder.maxItems;
        this.properties = builder.properties;
        this.required = builder.required;
        this.items = builder.items;
    }

    public Type getType() {
        return type;
    }

    public String getFormat() {
        return format;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public List<String> getEnumeration() {
        return enumeration;
    }

    public String getMaxItems() {
        return maxItems;
    }

    public Map<String, Schema> getProperties() {
        return properties;
    }

    public List<String> getRequired() {
        return required;
    }

    public Schema getItems() {
        return items;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Type type;
        private String format;
        private String description;
        private Boolean nullable;
        private List<String> enumeration;
        private String maxItems;
        private Map<String, Schema> properties;
        private List<String> required;
        private Schema items;

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder nullable(Boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        public Builder enumeration(List<String> enumeration) {
            this.enumeration = enumeration;
            return this;
        }

        public Builder maxItems(String maxItems) {
            this.maxItems = maxItems;
            return this;
        }

        public Builder properties(Map<String, Schema> properties) {
            this.properties = properties;
            return this;
        }

        public Builder required(List<String> required) {
            this.required = required;
            return this;
        }

        public Builder items(Schema items) {
            this.items = items;
            return this;
        }

        public Schema build() {
            return new Schema(this);
        }
    }

    public boolean isEffectiveEmptyObject() {
        if (type == Type.OBJECT) {
            if (properties.isEmpty()) {
                return true;
            } else {
                Schema contentSchema = properties.get("content");
                if (contentSchema == null) {
                    return true;
                }
                if (contentSchema.type == Type.OBJECT) {
                    return (contentSchema.properties == null) || contentSchema.properties.isEmpty();
                }
            }
        }
        return false;
    }
}

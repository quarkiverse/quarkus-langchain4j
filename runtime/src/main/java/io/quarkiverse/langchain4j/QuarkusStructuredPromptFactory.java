package io.quarkiverse.langchain4j;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;
import io.quarkiverse.langchain4j.runtime.StructuredPromptsRecorder;
import io.quarkiverse.langchain4j.runtime.prompt.Mappable;
import io.quarkus.arc.Arc;

public class QuarkusStructuredPromptFactory implements StructuredPromptFactory {
    @Override
    public Prompt toPrompt(Object structuredPrompt) {
        Class<?> structuredPromptClass = structuredPrompt.getClass();
        String promptTemplateString = StructuredPromptsRecorder.get(structuredPromptClass.getName());
        if (promptTemplateString == null) {
            // TODO: it would be great if we could verify call sites at build time
            throw new IllegalArgumentException(
                    String.format(
                            "%s should be annotated with @StructuredPrompt to be used as a structured prompt",
                            structuredPromptClass.getName()));
        }

        Map<String, Object> variables;
        if (structuredPrompt instanceof Mappable) {
            variables = ((Mappable) structuredPrompt).obtainFieldValuesMap();
        } else {
            variables = ObjectMapperHolder.MAPPER.convertValue(structuredPrompt,
                    ObjectMapperHolder.MAP_TYPE_REFERENCE);
        }

        PromptTemplate promptTemplate = PromptTemplate.from(promptTemplateString);
        return promptTemplate.apply(variables);
    }

    private static class ObjectMapperHolder {
        private static final ObjectMapper MAPPER;
        private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
        };

        static {
            ObjectMapper copied = Arc.container().instance(ObjectMapper.class).get().copy();
            copied.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE).setVisibility(PropertyAccessor.FIELD,
                    JsonAutoDetect.Visibility.ANY);
            MAPPER = copied;
        }
    }
}

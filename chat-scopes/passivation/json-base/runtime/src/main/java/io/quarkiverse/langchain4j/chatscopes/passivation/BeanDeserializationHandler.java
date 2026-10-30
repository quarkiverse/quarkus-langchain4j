package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;

import io.quarkus.arc.Subclass;

/**
 * This handler is used to deserialize custom fields added by passivation
 */
public class BeanDeserializationHandler extends DeserializationProblemHandler {
    @Override
    public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p, JsonDeserializer<?> deserializer,
            Object beanOrClass, String propertyName) throws IOException {
        if (!propertyName.equals(AbstractJsonChatScopeStore.PASSIVATED_DECORATORS)) {
            return false;
        }
        if (!(beanOrClass instanceof Subclass)) {
            return false;
        }
        Map<String, String> decorators = p.readValueAs(Map.class);
        try {
            AbstractJsonChatScopeStore.deserializeDecorators(beanOrClass, decorators);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return false;
    }
}

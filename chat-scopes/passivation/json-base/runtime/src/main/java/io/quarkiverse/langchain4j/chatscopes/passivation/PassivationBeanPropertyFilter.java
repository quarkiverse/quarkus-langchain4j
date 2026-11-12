package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.Map;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;

import io.quarkus.arc.Subclass;

/**
 * Does 2 things:
 * 1. Tests to see if a field should be ignored for serialization
 * 2. Subclass beans (beans with interceptors or decorators) have special serialization rules
 */
public class PassivationBeanPropertyFilter implements PropertyFilter {

    @Override
    public void serializeAsField(Object pojo, JsonGenerator gen, SerializerProvider prov, PropertyWriter writer)
            throws Exception {
        if (writer.getMember() == null) {
            writer.serializeAsField(pojo, gen, prov);
            return;
        }
        Object target = writer.getMember().getValue(pojo);
        if (target == null) {
            writer.serializeAsField(pojo, gen, prov);
            return;
        }

        AnnotatedElement annotatedElement = writer.getMember().getAnnotated();
        if (!shouldSkip(pojo, gen, annotatedElement, target)) {
            writer.serializeAsField(pojo, gen, prov);
        }
    }

    private static boolean shouldSkip(Object pojo, JsonGenerator gen, AnnotatedElement annotatedElement, Object target)
            throws Exception {
        if (annotatedElement == null && ignoredClass(target)) {
            return true;
        } else {
            if (pojo instanceof Subclass) {
                Field field = (Field) annotatedElement;
                if (field.getDeclaringClass().equals(pojo.getClass())) {
                    // decorator fields follow no naming convention so
                    // we key off a stable Arc-generated marker field to
                    // trigger writing the decorators only once.
                    // (Quarkus 3 used "arc$constructed"; Quarkus 4 uses "arc$alive".)
                    if (field.getName().equals("arc$alive")) {
                        Map<String, String> decorators = AbstractJsonChatScopeStore.serializeDecorators(pojo);
                        if (decorators.isEmpty()) {
                            return true;
                        }
                        gen.writeFieldName(AbstractJsonChatScopeStore.PASSIVATED_DECORATORS);
                        gen.writeObject(decorators);
                    }
                    return true;
                }
            }
            if (!annotatedElement.isAnnotationPresent(io.quarkiverse.langchain4j.chatscopes.Passivate.class)) {
                if (ignoredClass(target)) {
                    return true;
                }
                if (ignoredAnnotations(annotatedElement)) {
                    return true;
                }
                if (isInjectionPoint(annotatedElement)) {
                    Class targetClass = target.getClass();
                    if (target instanceof Subclass) {
                        targetClass = targetClass.getSuperclass();
                    }
                    if (!targetClass.isAnnotationPresent(Dependent.class)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean ignoredClass(Object target) {
        for (Class<?> ignored : JsonPassivation.ignoredClasses) {
            if (ignored.isInstance(target)) {
                return true;
            }
        }
        return false;
    }

    public static boolean ignoredAnnotations(AnnotatedElement annotatedElement) {
        for (Class<? extends Annotation> ignored : JsonPassivation.ignoredAnnotations) {
            if (annotatedElement.isAnnotationPresent(ignored)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInjectionPoint(AnnotatedElement annotatedElement) {
        boolean injected = annotatedElement.isAnnotationPresent(Inject.class);
        for (Annotation annotation : annotatedElement.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Qualifier.class)) {
                injected = true;
            }
        }
        return injected;
    }

    @Override
    public void serializeAsElement(Object elementValue, JsonGenerator gen, SerializerProvider prov, PropertyWriter writer)
            throws Exception {
        writer.serializeAsElement(elementValue, gen, prov);

    }

    @Override
    public void depositSchemaProperty(PropertyWriter writer, ObjectNode propertiesNode, SerializerProvider provider)
            throws JsonMappingException {
        writer.depositSchemaProperty(propertiesNode, provider);

    }

    @Override
    public void depositSchemaProperty(PropertyWriter writer, JsonObjectFormatVisitor objectVisitor, SerializerProvider provider)
            throws JsonMappingException {
        writer.depositSchemaProperty(objectVisitor, provider);
    }
}

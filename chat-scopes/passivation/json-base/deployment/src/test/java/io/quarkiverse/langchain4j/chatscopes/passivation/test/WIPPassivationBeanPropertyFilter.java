package io.quarkiverse.langchain4j.chatscopes.passivation.test;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;

import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.Subclass;
import io.quarkus.arc.impl.InterceptedMethodMetadata;

public class WIPPassivationBeanPropertyFilter implements PropertyFilter {
    @Override
    public void serializeAsField(Object pojo, JsonGenerator gen, SerializerProvider prov, PropertyWriter writer)
            throws Exception {
        System.out.println("serializeAsField " + writer.getName());
        Object target = writer.getMember().getValue(pojo);
        if (target == null) {
            System.out.println("target is null");
            return;
        }
        if (target instanceof ClientProxy) {
            System.out.println("target is ClientProxy");
            return;
        }
        if (target instanceof InterceptedMethodMetadata) {
            System.out.println("target is InterceptedMethodMetadata");
            return;
        }
        if (writer.getAnnotation(ConfigProperty.class) != null) {
            System.out.println("writer is ConfigProperty");
            return;
        }
        if (target instanceof Subclass) {
            System.out.println("target is Subclass");
            Class superClass = target.getClass().getSuperclass();
            prov.findValueSerializer(superClass, writer).serialize(target, gen, prov);
        } else {
            System.out.println("target is not Subclass");
            writer.serializeAsField(pojo, gen, prov);
        }
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

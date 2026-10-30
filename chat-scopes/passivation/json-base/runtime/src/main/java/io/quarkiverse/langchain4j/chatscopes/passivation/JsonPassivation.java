package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.impl.InterceptedMethodMetadata;

public class JsonPassivation {
    public static ObjectMapper mapper;
    public static Set<Class> ignoredClasses = new HashSet<>();
    public static Set<Class<? extends Annotation>> ignoredAnnotations = new HashSet<>();
    public static Set<ChatScopeSerializer> serializers = new HashSet<>();

    static {
        ignoredClasses.add(ClientProxy.class);
        ignoredClasses.add(InterceptedMethodMetadata.class);
        ignoredAnnotations.add(ConfigProperty.class);
        serializers.add(new AiServiceSerializer());
    }

}

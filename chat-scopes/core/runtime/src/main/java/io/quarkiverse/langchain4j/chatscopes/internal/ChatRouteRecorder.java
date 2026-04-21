package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.InvocationScoped;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ChatRouteRecorder {

    public static Map<String, ChatRouteExecution> routes = new ConcurrentHashMap<>();
    public static volatile BeanContainer CONTAINER = null;
    public static String defaultRoute = null;

    public void setContainer(BeanContainer container) {
        CONTAINER = container;
    }

    public void registerRoute(String frameName, Class<?> targetClass, String methodName, boolean isDefault) {
        ChatRouteExecution chatFrameExecution = new ReflectiveChatRouteExecution(targetClass,
                resolveMethod(targetClass, methodName));
        routes.put(frameName, chatFrameExecution);
        if (isDefault) {
            defaultRoute = frameName;
        }
    }

    private static final String LOCAL_KEY_PREFIX = "io.quarkus.vertx.cdi-current-context";

    public RuntimeValue<List<String>> getIgnoredArcContextKeysSupplier() {
        return new RuntimeValue<>(
                List.of(LOCAL_KEY_PREFIX + ChatScoped.class.getName(), LOCAL_KEY_PREFIX + InvocationScoped.class.getName()));
    }

    protected Method resolveMethod(Class<?> targetClass, String methodName) {
        for (Method method : targetClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

}

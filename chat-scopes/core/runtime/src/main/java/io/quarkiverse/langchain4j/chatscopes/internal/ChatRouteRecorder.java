package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
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

    public record ExceptionHandlerHolder(Class<? extends Throwable> exceptionType, ExceptionMapper.Handler handler) {

    }

    public static Map<String, List<ExceptionHandlerHolder>> exceptionHandlers = new ConcurrentHashMap<>();
    public static List<ExceptionHandlerHolder> defaultExceptionHandlers = new ArrayList<>();

    public void registerExceptionHandler(List<String> routes, Class<?> targetClass, String methodName) {
        ReflectiveExceptionHandler exceptionHandler = new ReflectiveExceptionHandler(targetClass,
                resolveMethod(targetClass, methodName));
        if (routes == null || routes.isEmpty()) {
            defaultExceptionHandlers.add(new ExceptionHandlerHolder(exceptionHandler.exceptionType(), exceptionHandler));
            return;
        }
        for (String route : routes) {
            exceptionHandlers.computeIfAbsent(route, k -> new ArrayList<>())
                    .add(new ExceptionHandlerHolder(exceptionHandler.exceptionType(), exceptionHandler));
        }
    }

    public void setContainer(BeanContainer container) {
        CONTAINER = container;
    }

    public void registerRoute(String routeName, Class<?> targetClass, String methodName, boolean isDefault) {
        ChatRouteExecution execution = new ReflectiveChatRouteExecution(targetClass,
                resolveMethod(targetClass, methodName));
        routes.put(routeName, execution);
        if (isDefault) {
            defaultRoute = routeName;
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

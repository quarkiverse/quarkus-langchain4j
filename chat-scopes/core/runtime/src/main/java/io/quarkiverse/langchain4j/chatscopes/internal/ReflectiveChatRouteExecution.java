package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecution;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.EventType;
import io.quarkus.arc.Arc;
import io.quarkus.arc.runtime.BeanContainer;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

public class ReflectiveChatRouteExecution implements ChatRouteExecution {
    private static final Logger log = Logger.getLogger(ReflectiveChatRouteExecution.class);
    private final Class<?> beanClass;
    private final Method method;
    protected volatile BeanContainer.Factory<?> factory;
    protected List<ParameterResolver> parameterResolvers = new ArrayList<>();

    static ParameterResolver chatRouteContextProvider = (ctx) -> ctx;
    static ParameterResolver userMessageProvider = (ctx) -> ctx.request().userMessage();
    static EventResolver stringReturnValueResolver = (context, obj) -> context.response().message((String) obj);
    static EventResolver objectReturnValueResolver = (context, obj) -> context.response().objectMessage(obj);

    EventResolver mapper;
    Class<?> resultMapperClass;
    boolean isMulti = false;

    interface ParameterResolver {
        Object resolve(ChatRouteContext context);
    }

    interface EventResolver {
        void resolve(ChatRouteContext context, Object value);
    }

    public ReflectiveChatRouteExecution(Class<?> beanClass, Method method) {
        this.beanClass = beanClass;
        this.method = method;

        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(UserMessage.class)) {
                parameterResolvers.add(userMessageProvider);
            } else if (parameter.getType().isAssignableFrom(ChatRouteContext.class)) {
                parameterResolvers.add(chatRouteContextProvider);
            } else {
                String key = parameter.getName();
                Type type = parameter.getParameterizedType();
                parameterResolvers.add((ctx) -> ctx.request().data(key, type));
            }
        }
        if (method.getReturnType().equals(Multi.class)) {
            if (!(method.getGenericReturnType() instanceof ParameterizedType)) {
                throw new IllegalArgumentException("Only Multi<String> is supported");
            }
            ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
            Type resultType = parameterizedType.getActualTypeArguments()[0];
            Class<?> resultClass = resolveClass(resultType);
            if (!String.class.equals(resultClass)) {
                throw new IllegalArgumentException("Only Multi<String> is supported");
            }
            isMulti = true;
        } else {
            mapper = resolveResultMapper(method.getAnnotation(EventType.class), method.getGenericReturnType(),
                    method.getReturnType());
        }
    }

    static Class<?> resolveClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        return null;
    }

    private EventResolver resolveResultMapper(EventType eventType, Type type, Class<?> clazz) {
        if (clazz.equals(Result.class)) {
            if (type != null && type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                Type resultType = parameterizedType.getActualTypeArguments()[0];
                Class<?> resultClass = resolveClass(resultType);
                EventResolver resultMapper = resolveResultMapper(eventType, resultType, resultClass);
                if (resultMapper != null) {
                    return (context, obj) -> {
                        Result<?> result = (Result<?>) obj;
                        if (result.content() != null) {
                            resultMapper.resolve(context, result.content());
                        } else {
                            for (ToolExecution execution : result.toolExecutions()) {
                                if (execution.resultObject() != null) {
                                    resolveResultMapper(eventType, execution.resultObject().getClass(),
                                            execution.resultObject().getClass())
                                            .resolve(context, execution.resultObject());
                                }
                            }
                        }
                    };
                }
            }
            return (context, obj) -> {
                Result<?> result = (Result<?>) obj;
                if (result.content() != null) {
                    resolveResultMapper(eventType, result.content().getClass(), result.content().getClass())
                            .resolve(context, result.content());
                } else {
                    for (ToolExecution execution : result.toolExecutions()) {
                        if (execution.resultObject() != null) {
                            resolveResultMapper(eventType, execution.resultObject().getClass(),
                                    execution.resultObject().getClass())
                                    .resolve(context, execution.resultObject());
                        }
                    }
                }
            };
        }
        EventType eventTypeAnnotation = eventType != null ? eventType : clazz.getAnnotation(EventType.class);
        if (eventTypeAnnotation != null) {
            return (context, obj) -> context.response().event(eventTypeAnnotation.value(), obj);
        }

        if (clazz.equals(String.class)) {
            return stringReturnValueResolver;
        } else {
            return objectReturnValueResolver;
        }
    }

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public <T> T getBean() {
        return (T) Arc.container().instance(beanClass).get();
    }

    @Override
    public Uni<Void> execute() {
        Object instance = Arc.container().instance(beanClass).get();
        try {
            Object returnValue = null;
            ChatRouteContext context = ChatRouteRecorder.CONTAINER.beanInstance(ChatRouteContext.class);
            if (parameterResolvers.size() == 0) {
                returnValue = method.invoke(instance);
            } else {
                Object[] parameters = new Object[parameterResolvers.size()];
                for (int i = 0; i < parameterResolvers.size(); i++) {
                    parameters[i] = parameterResolvers.get(i).resolve(context);
                }
                returnValue = method.invoke(instance, parameters);
            }
            if (returnValue != null) {
                if (isMulti) {
                    Multi<String> multi = (Multi<String>) returnValue;
                    return multi.emitOn(Infrastructure.getDefaultExecutor()).onFailure().invoke(e -> {
                        log.error("Error streaming", e);
                        ((ServerChatRouteContext.ServerResponseChannel) context.response()).failed("StreamingFailure");
                    }).onItem()
                            .invoke(token -> ((ServerChatRouteContext.ServerResponseChannel) context.response()).stream(token))
                            .collect().last().map(item -> null);
                } else {
                    mapper.resolve(context, returnValue);
                }
            }
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else {
                throw new RuntimeException(e.getCause());
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}

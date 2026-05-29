package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.SystemFailure;
import io.quarkus.arc.Arc;

public class ReflectiveExceptionHandler implements ExceptionMapper.Handler {
    static Logger log = Logger.getLogger(ReflectiveExceptionHandler.class);
    private final Class<?> targetClass;
    private final Method method;
    private final boolean isStatic;
    private final Class<? extends Throwable> exceptionType;

    public ReflectiveExceptionHandler(Class<?> targetClass, Method method) {
        this.targetClass = targetClass;
        this.method = method;
        this.isStatic = Modifier.isStatic(method.getModifiers());
        if (method.getParameterTypes().length != 2) {
            throw new IllegalArgumentException("Exception handler method must have exactly two parameters");
        }
        if (!Throwable.class.isAssignableFrom(method.getParameterTypes()[0])) {
            throw new IllegalArgumentException("First parameter must be Throwable");
        }
        if (!ChatRouteContext.class.isAssignableFrom(method.getParameterTypes()[1])) {
            throw new IllegalArgumentException("Second parameter must be ChatRouteContext");
        }
        this.exceptionType = method.getParameterTypes()[0].asSubclass(Throwable.class);
    }

    public Class<? extends Throwable> exceptionType() {
        return exceptionType;
    }

    @Override
    public void handle(Throwable t, ChatRouteContext context) {
        if (isStatic) {
            try {
                method.invoke(null, t, context);
            } catch (InvocationTargetException e) {
                throw new SystemFailure("Error invoking exception handler method", e.getCause());
            } catch (IllegalArgumentException e) {
                //log.error("Error invoking exception handler method:\n\t" + method.toGenericString() + "\n\t"
                //       + t.getClass().getName());
                throw new SystemFailure("Error invoking exception handler method", e);
            } catch (Exception e) {
                throw new SystemFailure("Error invoking exception handler method", e);
            }
        } else {
            Object instance = Arc.container().instance(targetClass).get();
            try {
                method.invoke(instance, t, context);
            } catch (InvocationTargetException e) {
                throw new SystemFailure("Error invoking exception handler method", e.getCause());
            } catch (Exception e) {
                throw new SystemFailure("Error invoking exception handler method", e);
            }
        }
    }
}

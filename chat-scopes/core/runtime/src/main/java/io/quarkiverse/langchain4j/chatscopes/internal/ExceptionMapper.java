package io.quarkiverse.langchain4j.chatscopes.internal;

import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.ChatRoutes;

public class ExceptionMapper {
    static Logger log = Logger.getLogger(ChatRouter.class);

    public interface Handler {
        void handle(Throwable e, ChatRouteContext context);
    }

    public static Handler handle(UnhandledApplicationException e) {
        try {
            Throwable cause = e.getCause();
            String currentRoute = ChatRoutes.current();
            if (currentRoute == null) {
                return handleDefault(cause);
            }
            List<ChatRouteRecorder.ExceptionHandlerHolder> handlers = ChatRouteRecorder.exceptionHandlers.get(currentRoute);

            if (handlers != null && !handlers.isEmpty()) {
                Handler handler = resolveHandler(cause.getClass(), handlers);
                if (handler != null) {
                    return handler;
                }
            }

            return handleDefault(cause);
        } catch (Exception ex) {
            log.error("Error mapping exception", ex);
            return null;
        }
    }

    private static Handler handleDefault(Throwable cause) {
        List<ChatRouteRecorder.ExceptionHandlerHolder> handlers;
        handlers = ChatRouteRecorder.defaultExceptionHandlers;
        if (!handlers.isEmpty()) {
            return resolveHandler(cause.getClass(), handlers);
        }
        return null;
    }

    public static Handler resolveHandler(Class<? extends Throwable> exceptionClass,
            List<ChatRouteRecorder.ExceptionHandlerHolder> handlers) {
        for (ChatRouteRecorder.ExceptionHandlerHolder handler : handlers) {
            if (handler.exceptionType().equals(exceptionClass)) {
                return handler.handler();
            }
        }
        if (exceptionClass.getSuperclass() != null && exceptionClass.getSuperclass() != Object.class) {
            return resolveHandler(exceptionClass.getSuperclass().asSubclass(Throwable.class), handlers);
        }
        return null;
    }
}

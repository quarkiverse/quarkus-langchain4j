package io.quarkiverse.langchain4j.chatscopes;

import jakarta.enterprise.context.ContextNotActiveException;

import io.quarkiverse.langchain4j.chatscopes.internal.ChatRouteExecution;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatRouter;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext.ChatScopeImpl;

public interface ChatRoutes {
    /**
     * Get the current route name. Current routes are attached to the current chat scope.
     *
     * @return
     */
    static String current() {
        if (!ChatScope.isActive()) {
            throw new ContextNotActiveException();
        }
        return ChatScope.current().getRoute();
    }

    /**
     * Set the current route.
     *
     * @param route
     */
    static void current(String route) {
        if (!ChatScope.isActive()) {
            throw new ContextNotActiveException();
        }
        ChatScope.current().setRoute(route);
    }

    /**
     * Execute the current route.
     */
    static void execute() {
        ChatRouter.executeCurrent((ChatScopeImpl) ChatScope.current());
    }

    /**
     * Execute a specific route.
     *
     * @param route
     */
    static void execute(String route) {
        ChatRouter.executeRoute(route);
    }

    /**
     * Get the CDI bean for the current route.
     *
     * @return
     */
    static <T> T bean() {
        ChatRouteExecution routeExecution = ChatRouter.routeExecution(current());
        if (routeExecution == null) {
            throw new RouteNotFound();
        }
        return routeExecution.getBean();
    }

    /**
     * Get the CDI bean for a specific route.
     *
     * @param route
     * @return
     */
    static <T> T bean(String route) {
        ChatRouteExecution routeExecution = ChatRouter.routeExecution(route);
        if (routeExecution == null) {
            throw new RouteNotFound();
        }
        return routeExecution.getBean();
    }

    /**
     * Get the class of the CDI bean for the current route.
     *
     * @return
     */
    static Class<?> beanClass() {
        ChatRouteExecution routeExecution = ChatRouter.routeExecution(current());
        if (routeExecution == null) {
            throw new RouteNotFound();
        }
        return routeExecution.getBeanClass();
    }

    /**
     * Get the class of the CDI bean for a specific route.
     *
     * @param route
     * @return
     */
    static Class<?> beanClass(String route) {
        ChatRouteExecution routeExecution = ChatRouter.routeExecution(route);
        if (routeExecution == null) {
            throw new RouteNotFound();
        }
        return routeExecution.getBeanClass();
    }
}

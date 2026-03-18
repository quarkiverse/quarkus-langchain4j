package io.quarkiverse.langchain4j.chatscopes.internal;

import jakarta.enterprise.context.ContextNotActiveException;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.chatscopes.ChatRouteApplicationException;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.RouteNotFound;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext.ChatScopeImpl;
import io.smallrye.mutiny.Uni;

public class ChatRouter {
    static Logger log = Logger.getLogger(ChatRouter.class);

    public String connect(String route) {
        if (route == null) {
            route = ChatRouteRecorder.defaultRoute;
        }
        if (route == null) {
            throw new RouteNotFound();
        }
        if (!ChatRouteRecorder.routes.containsKey(route)) {
            throw new RouteNotFound();
        }
        String id = ChatScopeManagedContext.INSTANCE.createTopScope(route);
        return id;
    }

    public static void executeCurrent(ChatScopeImpl scope) {
        log.debugf("Executing chat route: %s::%s", scope.getId(), scope.getRoute());
        String route = scope.getRoute();
        if (route == null) {
            throw new RouteNotFound();
        }
        executeRoute(route);
    }

    public static ChatRouteExecution routeExecution(String route) {
        return ChatRouteRecorder.routes.get(route);
    }

    public static void executeRoute(String route) {
        ChatRouteExecution execution = ChatRouteRecorder.routes.get(route);
        if (execution == null) {
            throw new RouteNotFound();
        }
        Uni<Void> result = execution.execute();
        if (result != null) {
            result.await().indefinitely();
        }
    }

    public void execute(String scopeId, ServerChatRouteContext ctx) {
        try {
            log.debugf("Attaching to chat scope: %s", scopeId);
            ChatScopeImpl scope = ChatScopeManagedContext.INSTANCE.activate(scopeId);
            RequestScopedChatRouteContext delegate = ChatRouteRecorder.CONTAINER
                    .beanInstance(RequestScopedChatRouteContext.class);
            delegate.setDelegate(ctx);
            log.debugf("Executing chat route: %s::%s", scope.getId(), scope.getRoute());
            executeCurrent(scope);
            log.debugv("Returning from router");
            wipeMemoryIfScheduled();
            ctx.response().completed(ChatScope.id());
        } catch (ChatRouteApplicationException e) {
            log.error("Application exception", e);
            wipeMemoryIfScheduled();
            ctx.response().completed(ChatScope.id());
        } catch (ContextNotActiveException e) {
            log.error("Session not active", e);
            wipeMemoryIfScheduled();
            ctx.response().sessionNotActive();
        } catch (Exception e) {
            log.error("Error executing chat route", e);
            wipeMemoryIfScheduled();
            ctx.response().serverError();
        } finally {
            ChatScopeManagedContext.INSTANCE.deactivate();
        }
    }

    private void wipeMemoryIfScheduled() {
        ChatScopeMemoryImpl.executeScheduledWipes();
    }
}

package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ContextNotActiveException;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.PostActivate;
import io.quarkiverse.langchain4j.chatscopes.PrePassivate;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ContextInstanceHandle;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.impl.LazyValue;

public class ChatScopeStoreManager {
    static LazyValue<ChatScopeStore> current = new LazyValue<>(
            () -> Arc.container().instance(ChatScopeStore.class).get());

    static AnnotatedMethodCache postActivateMethods = new AnnotatedMethodCache(PostActivate.class);
    static AnnotatedMethodCache prePassivateMethods = new AnnotatedMethodCache(PrePassivate.class);

    public static ChatScopeManagedContext.ChatScopeImpl activate(String scopeId) {
        ChatScopeStore store = current.get();
        if (store == null) {
            return null;
        }
        return activate(store, scopeId);
    }

    private static ChatScopeManagedContext.ChatScopeImpl activate(ChatScopeStore store, String id) {
        ChatScopeStore.PassivatedScope scope = store.activate(id);
        if (scope == null) {
            return null;
        }
        ChatScopeManagedContext.ChatScopeImpl parent = null;
        if (scope.parent() != null) {
            parent = activate(store, scope.parent());
            if (parent == null) {
                throw new ContextNotActiveException("Parent scope with id " + scope.parent() + " is not active");
            }
        }
        return ChatScopeManagedContext.INSTANCE.create(scope.id(), scope.route(), parent);
    }

    public static Object activateBean(ChatScope scope, String beanId, Object bean) {
        ChatScopeStore store = current.get();
        if (store == null) {
            return null;
        }
        Object passivatedBean = store.activateBean(scope, beanId, bean);
        if (passivatedBean == null) {
            return null;
        }
        List<Method> methods = postActivateMethods.get(bean.getClass());
        invokeCallback(passivatedBean, methods);
        return passivatedBean;
    }

    // todo this is all reflection need to fix this
    private static void invokeCallback(Object bean, List<Method> methods) {
        for (Method method : methods) {
            try {
                method.invoke(bean);
            } catch (IllegalAccessException e) {

            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                } else {
                    throw new RuntimeException(e.getCause());
                }
            }
        }
    }

    public static void passivate(ChatScope chatScope) {
        ChatScopeStore store = current.get();
        if (store == null) {
            return;
        }
        ChatScopeStore.PassivateTransaction tx = store.beginPassivate();
        ChatScopeManagedContext.ChatScopeImpl scope = (ChatScopeManagedContext.ChatScopeImpl) chatScope;
        try {
            do {
                Map<InjectableBean<?>, Object> beans = new HashMap<>();
                for (ContextInstanceHandle<?> handle : scope.getBeans()) {
                    Object instance = handle.get();
                    List<Method> methods = prePassivateMethods.get(instance.getClass());
                    invokeCallback(instance, methods);
                    beans.put(handle.getBean(), instance);
                }
                tx.passivate(scope, beans);
                scope = scope.parent;
            } while (scope != null);
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw new RuntimeException(e);
        }
    }

    public static void destroy(ChatScope scope) {
        ChatScopeStore store = current.get();
        if (store == null) {
            return;
        }
        store.destroy(scope);
    }
}

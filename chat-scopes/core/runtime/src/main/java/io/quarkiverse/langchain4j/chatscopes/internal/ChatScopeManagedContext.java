package io.quarkiverse.langchain4j.chatscopes.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ContextException;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.Contextual;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.CustomInjectableContext.CustomContextState;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ContextInstanceHandle;
import io.quarkus.arc.CurrentContext;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InjectableContext.ContextState;

public class ChatScopeManagedContext implements ContextState {
    public static final ChatScopeManagedContext INSTANCE = new ChatScopeManagedContext();

    static Logger log = Logger.getLogger(ChatScopeManagedContext.class);

    static CurrentContext<ChatScopeImpl> currentScope = Arc.container().getCurrentContextFactory()
            .create(ChatScoped.class);

    @Override
    public Map<InjectableBean<?>, Object> getContextualInstances() {
        throw new IllegalStateException("Should not be called");
    }

    Map<String, ChatScopeImpl> activeScopes = new ConcurrentHashMap<>();

    public class ChatScopeImpl extends CustomContextState implements ChatScope {
        final ChatScopeImpl parent;
        final String id;
        final Map<Object, Object> state = new ConcurrentHashMap<>();
        final List<ChatScopeImpl> children = new ArrayList<>();
        String route;
        volatile boolean destroyed = false;

        public ChatScopeImpl(String id, String route, ChatScopeImpl parent) {
            this.id = id;
            this.parent = parent;
            this.route = route;
            activeScopes.put(id, this);
        }

        public ChatScopeImpl(ChatScopeImpl parent, String route) {
            this(UUID.randomUUID().toString(), route, parent);
        }

        public ChatScopeImpl(String route) {
            this(null, route);
        }

        public ChatScopeImpl(ChatScopeImpl parent) {
            this(parent, parent.route);
        }

        public ChatScopeImpl() {
            this(null, null);
        }

        public Map<Object, Object> scopeState() {
            return state;
        }

        public ChatScopeImpl addChild(String id, String route) {
            ChatScopeImpl child = new ChatScopeImpl(id, route, this);
            children.add(child);
            return child;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getRoute() {
            return route;
        }

        @Override
        public void setRoute(String route) {
            this.route = route;
        }

        public <T> ContextInstanceHandle<T> get(Contextual<T> bean, boolean inherit) {
            ContextInstanceHandle<T> instanceHandle = super.get(bean);
            if (instanceHandle == null && inherit && parent != null) {
                instanceHandle = parent.get(bean, true);
            }
            return instanceHandle;
        }

        public ChatScopeImpl nest() {
            if (destroyed) {
                throw new ContextNotActiveException();
            }
            lock.lock();
            try {
                ChatScopeImpl child = new ChatScopeImpl(this);
                children.add(child);
                return child;
            } finally {
                lock.unlock();
            }
        }

        public ChatScopeImpl nest(String route) {
            if (destroyed) {
                throw new ContextNotActiveException();
            }
            lock.lock();
            try {
                ChatScopeImpl child = new ChatScopeImpl(this, route);
                children.add(child);
                return child;
            } finally {
                lock.unlock();
            }
        }

        public void destroyChild(ChatScopeImpl child) {
            if (destroyed) {
                throw new ContextNotActiveException();
            }
            lock.lock();
            try {
                children.remove(child);
                child.destroy();
            } finally {
                lock.unlock();
            }
        }

        public boolean isTop() {
            return parent == null;
        }

        public void destroy() {
            if (destroyed) {
                return;
            }
            lock.lock();
            try {
                activeScopes.remove(getId());
                children.forEach(ChatScopeImpl::destroy);
                children.clear();
                super.destroy();
                destroyed = true;
            } finally {
                lock.unlock();
            }
        }
    }

    public boolean has(String id) {
        return activeScopes.containsKey(id);
    }

    public ChatScopeImpl activate(String id) {
        ChatScopeImpl context = activeScopes.get(id);
        if (context == null) {
            throw new ContextNotActiveException();
        }
        currentScope.set(context);
        return context;
    }

    public void deactivate() {
        currentScope.remove();
    }

    public boolean isActive() {
        return currentScope.get() != null;
    }

    public ChatScopeImpl currentContext() {
        return currentScope.get();
    }

    public ChatScopeImpl begin() {
        return begin(null);
    }

    public String createTopScope(String route) {
        ChatScopeImpl context = new ChatScopeImpl(route);
        return context.id;
    }

    public ChatScopeImpl begin(String route) {
        if (currentScope.get() != null) {
            throw new ContextException("Existing scope already active");
        }
        ChatScopeImpl context = new ChatScopeImpl(route);
        currentScope.set(context);
        return context;
    }

    public void end() {
        pop();
    }

    public ChatScopeImpl push() {
        ChatScopeImpl current = currentContext();
        ChatScopeImpl context = null;
        if (current == null) {
            context = new ChatScopeImpl();
        } else {
            context = current.nest();
        }
        currentScope.set(context);
        return context;
    }

    public ChatScopeImpl push(String route) {
        ChatScopeImpl current = currentContext();
        ChatScopeImpl context = null;
        if (current == null) {
            context = new ChatScopeImpl(route);
        } else {
            context = current.nest(route);
        }
        currentScope.set(context);
        return context;
    }

    public void route(String route) {
        ChatScopeImpl current = currentContext();
        if (current == null) {
            throw new ContextNotActiveException();
        }
        current.route = route;
    }

    public void pop() {
        ChatScopeImpl current = currentContext();
        if (current == null) {
            throw new ContextNotActiveException();
        }
        if (current.isTop()) {
            current.destroy();
            currentScope.set(null);
            return;
        }
        ChatScopeImpl parent = current.parent;
        parent.destroyChild(current);
        currentScope.set(parent);
    }

    public void destroyAll() {
        List<ChatScopeImpl> toDestroy = new ArrayList<>();
        toDestroy.addAll(this.activeScopes.values());
        toDestroy.stream().filter(context -> context.isTop()).forEach(ChatScopeImpl::destroy);
        activeScopes.clear();
    }

    public void destroy(String id) {
        ChatScopeImpl scope = activeScopes.get(id);
        if (scope == null) {
            log.warn("ChatScope with id " + id + " not found.  Unable to destroy");
            return;
        }
        scope.destroy();
    }

}

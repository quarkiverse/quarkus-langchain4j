package io.quarkiverse.langchain4j.chatscopes.internal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.transaction.TransactionScoped;

import io.quarkus.arc.ContextInstanceHandle;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InjectableContext;
import io.quarkus.arc.impl.ContextInstanceHandleImpl;

/**
 * {@link jakarta.enterprise.context.spi.Context} class which defines the {@link TransactionScoped} context.
 */
public abstract class CustomInjectableContext implements InjectableContext {

    @Override
    public void destroy() {
        if (!isActive()) {
            return;
        }

        CustomContextState contextState = state();
        if (contextState == null) {
            return;
        }
        contextState.destroy();
    }

    @Override
    public void destroy(Contextual<?> contextual) {
        if (!isActive()) {
            return;
        }
        CustomContextState contextState = state();
        if (contextState == null) {
            return;
        }
        contextState.destroy(contextual);
    }

    protected abstract CustomContextState state();

    @Override
    public ContextState getState() {
        if (!isActive()) {
            throw new ContextNotActiveException("No active transaction on the current thread");
        }

        CustomContextState contextState = state();
        if (contextState == null) {
            throw new ContextNotActiveException();
        }
        return contextState;
    }

    protected <T> ContextInstanceHandle<T> getInstanceHandle(Contextual<T> contextual, CustomContextState contextState) {
        return contextState.get(contextual);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        if (!isActive()) {
            throw new ContextNotActiveException();
        }
        if (contextual == null) {
            throw new IllegalArgumentException("Contextual parameter must not be null");
        }

        CustomContextState contextState = state();

        ContextInstanceHandle<T> instanceHandle = getInstanceHandle(contextual, contextState);
        if (instanceHandle != null) {
            return instanceHandle.get();
        } else if (creationalContext != null) {
            Lock beanLock = contextState.getLock();
            beanLock.lock();
            try {
                instanceHandle = getInstanceHandle(contextual, contextState);
                if (instanceHandle != null) {
                    return instanceHandle.get();
                }

                T createdInstance = contextual.create(creationalContext);
                instanceHandle = new ContextInstanceHandleImpl<>((InjectableBean<T>) contextual, createdInstance,
                        creationalContext);
                contextState.put(contextual, instanceHandle);
                return createdInstance;
            } finally {
                beanLock.unlock();
            }
        } else {
            return null;
        }
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        return get(contextual, null);
    }

    /**
     * Representing of the context state. It's a container for all available beans in the context.
     * It's filled during bean usage and cleared on destroy.
     */
    public static class CustomContextState implements ContextState {
        protected final Lock lock = new ReentrantLock();

        protected final ConcurrentMap<Contextual<?>, ContextInstanceHandle<?>> mapBeanToInstanceHandle = new ConcurrentHashMap<>();

        /**
         * Put the contextual bean and its handle to the container.
         *
         * @param bean bean to be added
         * @param handle handle for the bean which incorporates the bean, contextual instance and the context
         */
        <T> void put(Contextual<T> bean, ContextInstanceHandle<T> handle) {
            mapBeanToInstanceHandle.put(bean, handle);
        }

        /**
         * Remove the bean from the container.
         *
         * @param bean contextual bean instance
         */
        <T> void destroy(Contextual<T> bean) {
            ContextInstanceHandle<?> instance = mapBeanToInstanceHandle.remove(bean);
            if (instance != null) {
                instance.destroy();
            }
        }

        /**
         * Retrieve the bean saved in the container.
         *
         * @param bean retrieving the bean from the container, otherwise {@code null} is returned
         */
        <T> ContextInstanceHandle<T> get(Contextual<T> bean) {
            return (ContextInstanceHandle<T>) mapBeanToInstanceHandle.get(bean);
        }

        /**
         * Get all the beans from the container.
         *
         * @return
         */
        List<ContextInstanceHandle<?>> getBeans() {
            return mapBeanToInstanceHandle.values().stream()
                    .collect(Collectors.toList());
        }

        /**
         * Get all the beans of the given class from the container. Filtering by the class type.
         *
         * @param <T>
         * @param beanClass
         * @return
         */
        <T> List<ContextInstanceHandle<T>> getBeans(Class<T> beanClass) {
            return mapBeanToInstanceHandle.values().stream()
                    .filter(handle -> handle.isAvailable() && beanClass.isAssignableFrom(handle.get().getClass()))
                    .map(handle -> (ContextInstanceHandle<T>) handle)
                    .collect(Collectors.toList());
        }

        /**
         * Destroying all the beans in the container and clearing the container.
         */
        void destroy() {
            for (ContextInstanceHandle<?> handle : mapBeanToInstanceHandle.values()) {
                handle.destroy();
            }
            mapBeanToInstanceHandle.clear();
        }

        /**
         * Method required by the {@link io.quarkus.arc.InjectableContext.ContextState} interface
         * which is then used to get state of the scope in method {@link InjectableContext#getState()}
         *
         * @return list of context bean and the bean instances which are available in the container
         */
        @Override
        public Map<InjectableBean<?>, Object> getContextualInstances() {
            return mapBeanToInstanceHandle.values().stream()
                    .collect(Collectors.toMap(ContextInstanceHandle::getBean, ContextInstanceHandle::get));
        }

        /**
         * Gets the lock associated with this ContextState for synchronization purposes
         *
         * @return the lock for this ContextState
         */
        public Lock getLock() {
            return lock;
        }
    }
}

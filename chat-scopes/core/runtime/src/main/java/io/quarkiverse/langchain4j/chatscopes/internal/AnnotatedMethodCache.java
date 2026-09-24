package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.annotation.Annotation;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AnnotatedMethodCache {
    // A WeakReference wrapper that preserves the key's original hashCode and equals behavior
    private static class WeakKey<T> extends WeakReference<T> {
        private final int hashCode;

        public WeakKey(T key, ReferenceQueue<T> queue) {
            super(key, queue);
            this.hashCode = key.hashCode();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof WeakKey))
                return false;
            Object referent = this.get();
            return referent != null && referent.equals(((WeakKey<?>) obj).get());
        }
    }

    ConcurrentHashMap<WeakKey<Class<?>>, List<Method>> cache = new ConcurrentHashMap<>();
    Class<? extends Annotation> annotation;
    private final ReferenceQueue<Class<?>> queue = new ReferenceQueue<>();

    public AnnotatedMethodCache(Class<? extends Annotation> annotation) {
        this.annotation = annotation;
    }

    void find(Class<?> clazz, Set<String> nonPrivate, List<Method> methods) {
        if (clazz.getSuperclass() != null) {
            find(clazz.getSuperclass(), nonPrivate, methods);
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotation)) {
                if (!Modifier.isPrivate(method.getModifiers()) && nonPrivate.contains(method.getName())) {
                    continue;
                }
                if (!Modifier.isPrivate(method.getModifiers())) {
                    nonPrivate.add(method.getName());
                }
                method.setAccessible(true);
                methods.add(method);
            }
        }

    }

    public List<Method> get(Class<?> clazz) {
        List<Method> result = cache.get(new WeakKey<>(clazz, null));
        if (result == null) {
            result = cache.computeIfAbsent(new WeakKey<>(clazz, queue), k -> {
                List<Method> methods = new ArrayList<>();
                Set<String> nonPrivate = new HashSet<>();
                find(clazz, nonPrivate, methods);
                return methods;
            });
            purgeStaleEntries();
            return result;
        } else {
            return result;
        }

    }

    private void purgeStaleEntries() {
        WeakKey<?> staleKey;
        while ((staleKey = (WeakKey<?>) queue.poll()) != null) {
            cache.remove(staleKey);
        }
    }
}

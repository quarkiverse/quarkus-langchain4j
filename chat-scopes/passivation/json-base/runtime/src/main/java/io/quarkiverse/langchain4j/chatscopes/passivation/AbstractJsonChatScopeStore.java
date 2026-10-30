package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.decorator.Decorator;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.arc.InjectableBean;

public abstract class AbstractJsonChatScopeStore implements ChatScopeStore {

    public static final String PASSIVATED_DECORATORS = "passivated$decorators";

    public static Map<String, String> serializeDecorators(Object instance) throws Exception {
        Map<String, String> decorators = new HashMap<>();
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getName().startsWith("arc$"))
                continue;
            if (field.getName().equals("aroundInvokes"))
                continue;
            field.setAccessible(true);
            Object target = field.get(instance);
            if (target == null)
                continue;
            if (!target.getClass().isAnnotationPresent(Decorator.class)) {
                continue;
            }
            String json = JsonPassivation.mapper.writerFor(target.getClass())
                    .writeValueAsString(target);
            decorators.put(target.getClass().getName(), json);
        }
        return decorators;

    }

    public static void deserializeDecorators(Object instance, Map<String, String> decorators) throws Exception {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getName().startsWith("arc$"))
                continue;
            if (field.getName().equals("aroundInvokes"))
                continue;
            field.setAccessible(true);
            Object decorator = field.get(instance);
            if (decorator == null)
                continue;
            if (!decorator.getClass().isAnnotationPresent(Decorator.class)) {
                continue;
            }
            String json = decorators.get(decorator.getClass().getName());
            if (json == null)
                continue;
            Object target = JsonPassivation.mapper.readerForUpdating(decorator).readValue(json);
        }
    }

    public static ChatScopeSerializer serializerFor(Object instance) {
        for (ChatScopeSerializer serializer : JsonPassivation.serializers) {
            if (serializer.isHandled(instance)) {
                return serializer;
            }
        }
        return null;
    }

    class JsonPassivationTransaction implements PassivateTransaction {
        Map<String, ChatScopeRepresentation> passivated = new HashMap();

        @Override
        public void passivate(ChatScope scope, Map<InjectableBean<?>, Object> cdiBeans) {
            ChatScopeRepresentation rep = this.passivated.computeIfAbsent(scope.getId(), ChatScopeRepresentation::new);
            rep.parent = scope.parent() == null ? null : scope.parent().getId();
            rep.route = scope.getRoute();
            Map<String, String> beans = rep.beans;
            for (Map.Entry<InjectableBean<?>, Object> entry : cdiBeans.entrySet()) {
                InjectableBean<?> bean = entry.getKey();
                Object instance = entry.getValue();
                String json = null;
                ChatScopeSerializer serializer = serializerFor(instance);
                if (serializer != null) {
                    json = serializer.serialize(instance);
                } else {
                    try {
                        json = JsonPassivation.mapper.writeValueAsString(instance);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
                beans.put(bean.getIdentifier(), json);
            }
        }

        @Override
        public void commit() {
            save(passivated);
        }

        @Override
        public void rollback() {

        }
    }

    protected ConcurrentHashMap<String, ChatScopeRepresentation> chatScopeEntries = new ConcurrentHashMap<>();

    public void save(Map<String, ChatScopeRepresentation> scopes) {
        for (Map.Entry<String, ChatScopeRepresentation> entry : scopes.entrySet()) {
            ChatScopeRepresentation chatScopeEntry = chatScopeEntries.computeIfAbsent(entry.getKey(),
                    ChatScopeRepresentation::new);
            synchronized (chatScopeEntry) {
                chatScopeEntry.parent = entry.getValue().parent;
                chatScopeEntry.route = entry.getValue().route;
                chatScopeEntry.beans.putAll(entry.getValue().beans);
                try {
                    write(chatScopeEntry);
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }
            }
        }
    }

    protected abstract void write(ChatScopeRepresentation chatScopeEntry) throws Exception;

    @Override
    public PassivateTransaction beginPassivate() {
        return new JsonPassivationTransaction();
    }

    @Override
    public Object activateBean(ChatScope scope, String beanId, Object instance) {
        ChatScopeRepresentation chatScopeEntry = chatScopeEntries.get(scope.getId());
        if (chatScopeEntry == null) {
            return null;
        }
        String bean = chatScopeEntry.beans.get(beanId);
        if (bean == null) {
            return null;
        }
        ChatScopeSerializer serializer = serializerFor(instance);
        if (serializer != null) {
            try {
                return serializer.deserialize(bean, instance);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                return JsonPassivation.mapper.readerForUpdating(instance).readValue(bean);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public abstract ChatScopeRepresentation load(String chatScopeId);

    @Override
    public PassivatedScope activate(String chatScopeId) {
        ChatScopeRepresentation rep = load(chatScopeId);
        if (rep == null) {
            return null;
        }
        return new PassivatedScope(rep.id, rep.route, rep.parent);
    }
}

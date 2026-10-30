package io.quarkiverse.langchain4j.chatscopes.spi;

import java.util.Map;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkus.arc.InjectableBean;

public interface ChatScopeStore {

    public interface PassivateTransaction {
        /**
         * Called for each scope in a hierarchy of parent/child
         *
         * @param scope
         * @param beans
         */
        void passivate(ChatScope scope, Map<InjectableBean<?>, Object> beans);

        void commit();

        void rollback();
    }

    public record PassivatedScope(String id, String route, String parent) {
    }

    PassivateTransaction beginPassivate();

    PassivatedScope activate(String chatScopeId);

    /**
     *
     * @param scope
     * @param beanId
     * @param instance
     * @return null if the bean was not activated from passivation store
     */
    Object activateBean(ChatScope scope, String beanId, Object instance);

    void destroy(ChatScope scope);
}

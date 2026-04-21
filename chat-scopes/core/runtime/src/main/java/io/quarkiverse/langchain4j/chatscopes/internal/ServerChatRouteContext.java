package io.quarkiverse.langchain4j.chatscopes.internal;

import io.quarkiverse.langchain4j.chatscopes.ChatRouteConstants;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;

public interface ServerChatRouteContext extends ChatRouteContext {

    record SystemMessage(String type, Object data) {

    }

    public interface ServerResponseChannel extends ChatRouteContext.ResponseChannel {
        default void routeNotFound() {
            failed("RouteNotFound");
        }

        default void sessionNotActive() {
            failed("SessionNotActive");
        }

        default void serverError() {
            failed("ServerError");
        }

        default void stream(String packet) {
            event(ChatRouteConstants.STREAM, packet);
        }

        /**
         * finish chat message and respond to the client with current scope id
         *
         * @param scopeId
         */
        void completed(String scopeId);

        /**
         * completed, but a failure occurred
         *
         * @param msg
         */
        void failed(String msg);

    }

    @Override
    ServerResponseChannel response();

}

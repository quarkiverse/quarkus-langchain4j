package org.acme.example.gemini.aiservices;

import jakarta.inject.Singleton;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;

@Singleton
public class TestChatModelListener implements ChatModelListener {

    volatile boolean onRequestCalled;
    volatile boolean onResponseCalled;

    public void onRequest(ChatModelRequestContext context) {
        onRequestCalled = true;
    }

    public void onResponse(ChatModelResponseContext context) {
        onResponseCalled = true;
    }

}

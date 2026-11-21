package org.acme.example.openai.aiservices;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("assistant")
public class AssistantResource {

    private final Assistant assistant;
    private final ClassAssistant classAssistant;

    public AssistantResource(ChatModel chatModel, ClassAssistant classAssistant) {
        this.assistant = AiServices.create(Assistant.class, chatModel);
        this.classAssistant = classAssistant;
    }

    @GET
    public String get(@DefaultValue("Hello, my name is test") @RestQuery String message) {
        return assistant.chat(message);
    }

    @GET
    @Path("class")
    public String getFromClass(@DefaultValue("Hello, my name is test") @RestQuery String message) {
        return classAssistant.signedChat(message);
    }

    interface Assistant {

        String chat(String message);
    }

    @ApplicationScoped
    public static class Signature {

        public String sign(String text) {
            return text + " -- signed";
        }
    }

    /**
     * The class form of an AI service: a bean with an injected field, a lifecycle callback, a native AI method
     * implemented by the extension and a concrete method calling it.
     */
    @RegisterAiService
    public static class ClassAssistant {

        @Inject
        Signature signature;

        private boolean initialized;

        @PostConstruct
        void init() {
            initialized = true;
        }

        @UserMessage("{message}")
        public native String chat(String message);

        public String signedChat(String message) {
            if (!initialized) {
                throw new IllegalStateException("@PostConstruct did not run");
            }
            return signature.sign(chat(message));
        }
    }
}

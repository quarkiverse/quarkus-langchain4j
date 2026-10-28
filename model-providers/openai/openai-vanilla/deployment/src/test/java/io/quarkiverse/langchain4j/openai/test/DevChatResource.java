package io.quarkiverse.langchain4j.openai.test;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.config.ConfigProvider;

import dev.langchain4j.model.chat.ChatModel;

@Path("/dev-chat")
public class DevChatResource {

    @Inject
    ChatModel chatModel;

    @GET
    public String chat() {
        return chatModel.chat("hi");
    }

    @GET
    @Path("/marker")
    public String marker() {
        return "before-reload";
    }

    @GET
    @Path("/url")
    public String url() {
        return ConfigProvider.getConfig().getValue("quarkus.langchain4j.mock-api.url", String.class);
    }
}

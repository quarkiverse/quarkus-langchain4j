package org.acme.example.gemini.aiservices;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("assistant-with-tool")
public class AssistantWithToolsResource {

    private final Assistant assistant;

    @Inject
    AddContentTool tool;

    public AssistantWithToolsResource(Assistant assistant) {
        this.assistant = assistant;
    }

    @GET
    public String get(@RestQuery String message) {
        return assistant.chat(message) + "; " + tool.tool1Content;
    }

    @RegisterAiService(tools = AddContentTool.class)
    public interface Assistant {
        String chat(String userMessage);
    }

    @Singleton
    public static class AddContentTool {

        volatile String tool1Content;

        @Tool("Add content")
        void addContent(String content) {
            this.tool1Content = "Tool1: " + content;
        }
    }
}

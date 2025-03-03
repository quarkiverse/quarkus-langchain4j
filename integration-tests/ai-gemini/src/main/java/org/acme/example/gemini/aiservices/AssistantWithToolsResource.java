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

    @Inject
    TestChatModelListener chatModelListener;

    public AssistantWithToolsResource(Assistant assistant) {
        this.assistant = assistant;
    }

    @GET
    public String get(@RestQuery String message) {
        String response = assistant.chat(message);// + ";" + tool.getTool1Content();
        return response + ":" + chatModelListener.onRequestCalled + ":" + chatModelListener.onResponseCalled;
    }

    @RegisterAiService(tools = AddContentTool.class)
    public interface Assistant {
        String chat(String userMessage);
    }

    @Singleton
    public static class AddContentTool {

        volatile String tool1Content;

        @Tool("Duplicate content")
        public String duplicateContent(String content) {
            return content + ":" + content;
        }

        public String getTool1Content() {
            return tool1Content;
        }
    }
}

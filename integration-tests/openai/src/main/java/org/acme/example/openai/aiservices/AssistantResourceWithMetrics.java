package org.acme.example.openai.aiservices;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.micrometer.core.annotation.Timed;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("assistant-with-metrics")
public class AssistantResourceWithMetrics {

    private final Assistant1 assistant1;
    private final Assistant2 assistant2;

    public AssistantResourceWithMetrics(Assistant1 assistant1, Assistant2 assistant2) {
        this.assistant1 = assistant1;
        this.assistant2 = assistant2;
    }

    @GET
    @Path("a1")
    public String assistant1() {
        return assistant1.chat("test");
    }

    @GET
    @Path("a2")
    public String assistant2() {
        return assistant2.chat("test");
    }

    @GET
    @Path("a2c2")
    public String assistant2Chat2() {
        return assistant2.chat2("test");
    }

    @RegisterAiService
    interface Assistant1 {

        String chat(String message);
    }

    @RegisterAiService
    @Timed(extraTags = { "key", "value" })
    interface Assistant2 {

        String chat(String message);

        @Timed(value = "a2c2", description = "Assistant2#chat2")
        String chat2(String message);
    }
}

package org.acme.example.openai.aiservices;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.service.SystemMessage;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("assistant-with-metrics")
public class AssistantResourceWithMetrics {

    private final Assistant1 assistant1;
    private final Assistant2 assistant2;
    private final Assistant3 assistant3;

    public AssistantResourceWithMetrics(Assistant1 assistant1, Assistant2 assistant2, Assistant3 assistant3) {
        this.assistant1 = assistant1;
        this.assistant2 = assistant2;
        this.assistant3 = assistant3;
    }

    @GET
    @Path("a1")
    public String assistant1() {
        return assistant1.chat("Hello, answer with word 'test'");
    }

    @GET
    @Path("a2")
    public String assistant2() {
        return assistant2.chat("Hello, answer with word 'test'");
    }

    @GET
    @Path("a2c2")
    public String assistant2Chat2() {
        return assistant2.chat2("Hello, answer with word 'test'");
    }

    @GET
    @Path("a3")
    public String assistant3() {
        return assistant3.chat("Hello, answer with word 'test'");
    }

    @RegisterAiService
    interface Assistant1 {

        String chat(String message);
    }

    @RegisterAiService
    @Timed(extraTags = { "key", "value" })
    interface Assistant2 {

        String chat(String message);

        @Timed(value = "a2c2-timed", description = "Assistant2#chat2")
        @Counted(value = "a2c2-counted", description = "Assistant2#chat2")
        String chat2(String message);
    }

    @RegisterAiService
    interface Assistant3 {

        @SystemMessage("Template that never gets the proper {data}")
        String chat(String message);
    }
}

package io.quarkiverse.langchain4j.sample.compaction;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;

import dev.langchain4j.memory.ChatMemory;
import io.quarkiverse.langchain4j.compaction.CompactionProfiles;

/**
 * Two assistants, one per compaction profile. Each has a single conversation, whose memory id is the assistant name
 * ({@code summarizing} or {@code window}): profiles share the chat memory store, so ids must differ.
 */
@Path("/assistants")
public class AssistantResource {

    public record Answer(String answer, TokenGain.Stats tokens) {
    }

    @Inject
    SummarizingAssistant summarizing;

    @Inject
    WindowAssistant window;

    @Inject
    CompactionProfiles profiles;

    @Inject
    TokenGain gain;

    @GET
    @Path("/questions")
    public List<Transcript.RecallQuestion> questions() {
        return Transcript.QUESTIONS;
    }

    /**
     * Loads the recorded conversation into the chat memory of the assistant, without calling the model.
     */
    @POST
    @Path("/{name}/replay")
    public TokenGain.Stats replay(@PathParam("name") String name) {
        ChatMemory memory = switch (name) {
            case "summarizing" -> profiles.defaultProvider().get(name);
            case "window" -> profiles.provider("window").get(name);
            default -> throw new NotFoundException();
        };
        memory.clear();
        Transcript.messages().forEach(memory::add);
        // reading the memory compacts the replayed history (and calls the summarizer for the default profile)
        memory.messages();
        gain.resetCounters(name);
        return gain.get(name);
    }

    @POST
    @Path("/{name}/messages")
    @Consumes(MediaType.TEXT_PLAIN)
    public Answer ask(@PathParam("name") String name, String message) {
        String answer = switch (name) {
            case "summarizing" -> summarizing.chat(name, message);
            case "window" -> window.chat(name, message);
            default -> throw new NotFoundException();
        };
        return new Answer(answer, gain.get(name));
    }
}

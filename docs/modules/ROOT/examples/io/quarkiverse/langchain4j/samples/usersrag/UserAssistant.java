package io.quarkiverse.langchain4j.samples.usersrag;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

@RegisterAiService(retrievalAugmentor = UserRetrievalAugmentor.class)
@SystemMessage("You answer only from the documents provided to you.")
@ApplicationScoped
public interface UserAssistant {

    String chat(@MemoryId String memoryId, @UserMessage String question);

    String summarize(@MemoryId String memoryId, @UserMessage String text);

    Multi<String> stream(@MemoryId String memoryId, @UserMessage String question);
}

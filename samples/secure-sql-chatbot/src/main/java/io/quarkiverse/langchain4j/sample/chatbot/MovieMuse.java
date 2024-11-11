package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.SessionScoped;

@RegisterAiService(retrievalAugmentor = MovieMuseRetrievalAugmentor.class)
@SessionScoped
public interface MovieMuse {

    @SystemMessage("""
            You are MovieMuse, an AI answering questions about the top 100 movies from IMDB.
            Your response must be polite, use the same language as the question, and be relevant to the question.
            Don't use any knowledge that is not in the database.
            """)
    String chat(@UserMessage String question);
}

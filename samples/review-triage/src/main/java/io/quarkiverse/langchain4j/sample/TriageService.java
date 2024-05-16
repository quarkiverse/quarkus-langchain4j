package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface TriageService {

    @SystemMessage("""
            You work as a curator of online content.
            """)
    @UserMessage("""
            Your task is to process the post delimited by ---.
            Apply a sentiment analysis to the passed review to determine whether a post is sarcastic or not.
            Reply only with 'true' if the post is sarcastic or 'false' if it is not.
            
            ---
            {post}
            ---
            """)
    boolean triage(String post);

}

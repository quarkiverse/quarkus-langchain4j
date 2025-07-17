package io.quarkiverse.langchain4j.samples.fewshots;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("You are an assistant that classifies text sentiment.")
public interface SentimentAiService {

    @UserMessage("""
                INPUT: This product is fantastic!   // <1>
                OUTPUT: POSITIVE

                INPUT: Terrible customer service.
                OUTPUT: NEGATIVE

                INPUT: {text}
                OUTPUT:
            """)
    String classifySentiment(String text); // <2>
}
package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.web.search.WebSearchEngine;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class AdditionalTools {

    @Inject
    WebSearchEngine webSearchEngine;

    @Tool("Get today's date")
    public String getTodaysDate() {
        String date = DateTimeFormatter.ISO_DATE.format(LocalDate.now());
        Log.info("The model is asking for today's date, returning " + date);
        return date;
    }

}

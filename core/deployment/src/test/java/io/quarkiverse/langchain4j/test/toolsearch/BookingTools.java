package io.quarkiverse.langchain4j.test.toolsearch;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;

@ApplicationScoped
public class BookingTools {

    @Tool("Returns booking details")
    public String getBookingDetails() {
        return "REAL_TOOL_RESULT";
    }
}

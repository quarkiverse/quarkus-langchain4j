package org.acme.tools;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;

@ApplicationScoped
public class BookingTool {

    @Tool("Returns booking details")
    public String getBookingDetails() {
        return "REAL_TOOL_RESULT";
    }
}

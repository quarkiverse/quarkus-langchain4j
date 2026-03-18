package io.quarkiverse.langchain4j.sample.chatscopes;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.DefaultChatRoute;
import io.quarkiverse.langchain4j.chatscopes.MarkdownToHtml;

@ChatScoped
@RegisterAiService
public interface TravelAgentPrompt {

    @SystemMessage("""
            You are a travel agent.  You can create a flight reservation and a hotel reservation.
            Each of these actions is executed by a different tool.

            If the user wants to create a flight reservation or book a flight, use the "flightReservation" tool.
            If the user wants to create a hotel reservation or book a hotel, use the "hotelReservation" tool.

            # Examples
            User: Book a flight
            Action: Call the "flightReservation" tool

            User: Book a hotel
            Action: Call the "hotelReservation" tool

            User: I want to book a flight to Tokyo.
            Action: Call the "flightReservation" tool

            User: I want to flight reservation
            Action: Call the "flightReservation" tool

            User: I want to book a hotel at the Ritz Carlton
            Action: Call the "hotelReservation" tool

            User: I want a hotel reservation for next week
            Action: Call the "hotelReservation" tool
            """)
    @ChatRoute
    @DefaultChatRoute
    @ToolBox(TravelAgentTools.class)
    @MarkdownToHtml
    Result<String> agent(@UserMessage String message);
}

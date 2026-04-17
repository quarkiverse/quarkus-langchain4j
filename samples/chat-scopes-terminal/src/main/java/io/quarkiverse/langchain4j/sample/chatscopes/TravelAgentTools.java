package io.quarkiverse.langchain4j.sample.chatscopes;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.ChatRoutes;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkus.logging.Log;

import static io.quarkiverse.langchain4j.sample.chatscopes.Model.FLIGHT_ROUTE;
import static io.quarkiverse.langchain4j.sample.chatscopes.Model.HOTEL_ROUTE;
import static io.quarkiverse.langchain4j.sample.chatscopes.Model.Flight_Placeholder;
import static io.quarkiverse.langchain4j.sample.chatscopes.Model.Hotel_Placeholder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TravelAgentTools {
    @Inject
    ChatRouteContext ctx;

    @Tool(name = "flightReservation", value="Create a flight reservation.  Book a flight", returnBehavior = ReturnBehavior.IMMEDIATE)
    public void flightReservation() {
        // The user wants to make a flight reservation so we need to have
        // a nested converstation with a different prompt.
        // The push call here set's this up, creating a child nested chat scope and setting the route to the flight prompt.
        ChatScope.push(FLIGHT_ROUTE);

        // send the placeholder event to the web client
        // to give a visual indication of what the current conversation is.
        // The input text box should be replaced with the placeholder text.
        ctx.response().event(Flight_Placeholder);

        // This will execute the flight reservation prompt with the same user message.
        // Why do we do this?  The user message might have additional information to create the reservation
        // For instance, in the top level conversation the user might say "Book a flight to Dublin on June 10th".
        // The flight reservation prompt will need that information to start building the reservation.
        ChatRoutes.execute();
    }

    @Tool(name = "hotelReservation", value="Create a hotel reservation.  Book a hotel", returnBehavior = ReturnBehavior.IMMEDIATE)
    public void hotelReservation() {
        // The user wants to make a hotel reservation so we need to have
        // a nested converstation with a different prompt.
        // The push call here set's this up, creating a child nested chat scope and setting the route to the hotel prompt.
        ChatScope.push(HOTEL_ROUTE);

        // send the placeholder event to the web client
        // to give a visual indication of what the current conversation is.
        // The input text box should be replaced with the placeholder text.
        ctx.response().event(Hotel_Placeholder);

        // This will execute the hotel reservation prompt with the same user message.
        // Why do we do this?  The user message might have additional information to create the reservation
        // For instance, in the top level conversation the user might say "Book a hotel at the Ritz for June 10th".
        // The hotel reservation prompt will need that information to start building the reservation.
        ChatRoutes.execute();
    }
}

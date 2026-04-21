package io.quarkiverse.langchain4j.sample.chatscopes;

import java.util.Date;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScopeMemory;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import jakarta.inject.Inject;

@ChatScoped
public class FlightReservationManager {
    @Inject
    ChatRouteContext ctx;

    Model.FlightReservation flightReservation = new Model.FlightReservation();

    @Tool("Set the departure date")
    public void setDepartureDate(String date) {
        flightReservation.departureDate = date;
        // if you take a look at the web client, thinking events just output to the console.log
        ctx.response().thinking("Departure date set to " + date);
    
    }

    @Tool("Set the return date")
    public void setReturnDate(String date) {
        flightReservation.returnDate = date;
        // if you take a look at the web client, thinking events just output to the console.log
        ctx.response().thinking("Return date set to " + date);
    }

    @Tool("Set the departure airport")
    public void setDepartureAirport(String airport) {
        flightReservation.departureAirport = airport;
        // if you take a look at the web client, thinking events just output to the console.log
        ctx.response().thinking("Departure airport set to " + airport);
    }

    @Tool("Set the arrival airport")
    public void setArrivalAirport(String airport) {
        flightReservation.arrivalAirport = airport;
        // if you take a look at the web client, thinking events just output to the console.log
        ctx.response().thinking("Arrival airport set to " + airport);
    }

 
    @Tool(value = "Book the flight", returnBehavior = ReturnBehavior.IMMEDIATE)
    public void bookFlight() throws Exception {
        // the web client can render Object messages from any JSON in a generic way.
        ctx.response().objectMessage(flightReservation);
        ctx.response().message("I have collected everything I need.");
        ctx.response().message("I will now book the flight.");
        Thread.sleep(3000);
        ctx.response().message("Flight booked successfully");

        // Pop the chat scope stack.  This will revert back to the TravelAgent default route.
        // It will also cause this bean to be destroyed as this bean was created when
        // the flight chat scope was pushed.
        // Also, since the ReservationsPrompt is also @ChatScoped, any chat memory will be cleared as well for
        // those conversations with it.
        ChatScope.pop();

       // Send event to the web client to tell it to revert to the previous placeholder
       // this gives a visual indication of what the current conversation is.
        ctx.response().event(Model.POP_PLACEHOLDER_EVENT, "");
        // we don't need history of main menu
        ChatScopeMemory.clearMemory();

    }
}

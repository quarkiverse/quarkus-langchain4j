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
public class HotelReservationManager {

    @Inject
    ChatRouteContext ctx;

    // This is just a pojo that stores
    Model.HotelReservation hotelReservation = new Model.HotelReservation();

    @Tool("Set the departure date")
    public void setCheckInDate(String date) {
        hotelReservation.checkInDate = date;
        // if you take a look at the web client, thinking events just output to the console.log
        ctx.response().thinking("Check-in date set to " + date);
    }

    @Tool("Set the return date")
    public void setCheckOutDate(String date) {
        hotelReservation.checkOutDate = date;
        // if you take a look at the web client, thinking events just output to the console.log
        ctx.response().thinking("Check-out date set to " + date);
    }

    @Tool("Set the hotel name")
    public void setHotelName(String name) {
        hotelReservation.hotelName = name;
        // if you take a look at the web client, thinking events just output to the console.log
        ctx.response().thinking("Hotel name set to " + name);
    }

    @Tool("Set the room type")
    public void setRoomType(String type) {
        hotelReservation.roomType = type;
        // if you take a look at the web client, thinking events just output to the console.log
        ctx.response().thinking("Room type set to " + type);
    }

    @Tool(value ="Book the hotel", returnBehavior = ReturnBehavior.IMMEDIATE)
    public void bookHotel() throws Exception {
        // the web client can render Object messages from any JSON in a generic way.
        ctx.response().objectMessage(hotelReservation);
        ctx.response().message("I have collected everything I need.");
        ctx.response().message("I will now book the hotel.");
        Thread.sleep(3000);
        ctx.response().message("Hotel booked successfully");

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

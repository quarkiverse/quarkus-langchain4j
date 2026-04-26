package io.quarkiverse.langchain4j.sample.chatscopes;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import static io.quarkiverse.langchain4j.sample.chatscopes.Model.FLIGHT_ROUTE;
import static io.quarkiverse.langchain4j.sample.chatscopes.Model.HOTEL_ROUTE;

@ChatScoped
@RegisterAiService
public interface ReservationsPrompt {

    @SystemMessage("""
        You are a travel agent.  Chat with the customer to get the details for a flight reservation.
        When finished, book the flight.

        Output should be in plain text.

        You will only need the following details:
        - Departure date
        - Return date
        - Departure airport
        - Arrival airport
                """)
    @ChatRoute(FLIGHT_ROUTE)
    @ToolBox(FlightReservationManager.class)
    Result<String> flightReservation(@UserMessage String message);

    @SystemMessage("""
        You are a travel agent.  Chat with the customer to get the details for a hotel reservation.
        When finished, book the hotel.
        
        Output should be in plain text.

        You will only need the following details:
        - Check-in date
        - Check-out date
        - Hotel name
        - Room type
        """)
    @ChatRoute(HOTEL_ROUTE)
    @ToolBox(HotelReservationManager.class)
    Result<String> hotelReservation(@UserMessage String message);

}

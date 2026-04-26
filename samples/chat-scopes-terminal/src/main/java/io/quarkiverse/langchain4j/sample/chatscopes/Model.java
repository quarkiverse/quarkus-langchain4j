package io.quarkiverse.langchain4j.sample.chatscopes;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonValue;

import io.quarkiverse.langchain4j.chatscopes.EventType;

public interface Model {
    public static final String FLIGHT_ROUTE = "flight";
    public static final String HOTEL_ROUTE = "hotel";
    public static final String POP_PLACEHOLDER_EVENT = "PopPlaceholder";
    public static final String PUSH_PLACEHOLDER_EVENT = "PushPlaceholder";

    public static final PushPlaceholder Flight_Placeholder = new PushPlaceholder("Book your flight");
    public static final PushPlaceholder Hotel_Placeholder = new PushPlaceholder("Book your hotel");



    @EventType(PUSH_PLACEHOLDER_EVENT)
    public record PushPlaceholder(String placeholder) {
    }

    public class FlightReservation {
        public String departureDate;
        public String returnDate;
        public String departureAirport;
        public String arrivalAirport;
    }

    public class HotelReservation {
        public String checkInDate;
        public String checkOutDate;
        public String hotelName;
        public String roomType;
    }

    public class Trip {
        public FlightReservation flightReservation;
        public HotelReservation hotelReservation;
    }
}

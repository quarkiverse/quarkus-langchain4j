package io.quarkiverse.langchain4j.sample.chatbot;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WebSocketTickets {
    ConcurrentHashMap<String, String> tickets = new ConcurrentHashMap<>();

    public String getTicket() {
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, ticket);
        Log.infof("New WebSocket ticket %s", ticket);
        // This ticket may also be backed up by a ticket cookie
        return ticket;
    }

    public String removeTicket(String ticket) {
        return tickets.get(ticket);
    }
}

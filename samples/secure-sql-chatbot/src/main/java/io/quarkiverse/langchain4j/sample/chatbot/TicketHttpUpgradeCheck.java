package io.quarkiverse.langchain4j.sample.chatbot;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;

@ApplicationScoped
public class TicketHttpUpgradeCheck implements HttpUpgradeCheck {

    @Inject
    WebSocketTickets tickets;

    @Override
    public Uni<CheckResult> perform(HttpUpgradeContext context) {
        String ticket = context.httpRequest().getParam("ticket");
        if (ticket == null) {
            Log.warn("WebSocket ticket is null");
            return Uni.createFrom().item(CheckResult.rejectUpgradeSync(401));
        }
        if (tickets.removeTicket(ticket) == null) {
            Log.warnf("WebSocket ticket %s is invalid", ticket);
            return Uni.createFrom().item(CheckResult.rejectUpgradeSync(401));
        }
        // If the ticket was backed up by a ticket cookie then it can be checked here as
        // well.
        Log.infof("WebSocket ticket %s is valid", ticket);
        return Uni.createFrom().item(CheckResult.permitUpgradeSync());
    }

}

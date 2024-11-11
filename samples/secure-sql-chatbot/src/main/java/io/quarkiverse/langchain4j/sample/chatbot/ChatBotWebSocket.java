package io.quarkiverse.langchain4j.sample.chatbot;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.logging.Log;
import io.quarkus.oidc.IdToken;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;

import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;

@WebSocket(path = "/chatbot")
@Authenticated
public class ChatBotWebSocket {

    private final MovieMuse bot;

    @Inject
    @IdToken
    JsonWebToken idToken;

    public ChatBotWebSocket(MovieMuse bot) {
        this.bot = bot;
    }

    @OnOpen
    public String onOpen() {
        return "Hello, " + idToken.getName() + ", I'm MovieMuse, how can I help you?";
    }

    @OnTextMessage
    public String onMessage(String message) {
        try {
            return idToken.getName() + ", " + bot.chat(message);
        } catch (MissingMovieWatcherException ex) {
            Log.error(ex);
            return """
                    Sorry, %s, looks like you did not register your name and email correctly.
                    Please use '-Dname="%s"' (keep double quotes around your name) and '-Demail=%s' system properties at startup
                    """
                    .formatted(idToken.getName(), idToken.getName(), idToken.getClaim(Claims.email));
        } catch (Throwable ex) {
            Log.error(ex);
            return "Sorry, " + idToken.getName()
                    + ", an unexpected error occurred, can you please ask your question again ?";
        }
    }
}

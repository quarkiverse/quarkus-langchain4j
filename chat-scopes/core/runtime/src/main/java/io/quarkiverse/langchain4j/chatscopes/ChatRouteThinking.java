package io.quarkiverse.langchain4j.chatscopes;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.langchain4j.runtime.aiservice.ThinkingEmitted;

/**
 * Helper for forwarding model thinking output to the active chat route's
 * response channel from within a {@link io.quarkiverse.langchain4j.Thinking
 * &#64;Thinking}-annotated handler on a {@link ChatScoped &#64;ChatScoped} AI
 * service.
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;RegisterAiService
 *     &#64;ChatScoped
 *     interface WeaponBuilder {
 *         &#64;ChatRoute("weaponBuilder")
 *         String build(@UserMessage String msg);
 *
 *         @Thinking
 *         static void onThinking(ThinkingEmitted event) {
 *             ChatRouteThinking.forward(event);
 *         }
 *     }
 * }
 * </pre>
 *
 * When called outside an active chat scope (or when no chat route context is
 * resolvable in CDI), the call is a no-op so the same handler can be reused
 * across services that may or may not run inside a chat route.
 */
public final class ChatRouteThinking {

    private ChatRouteThinking() {
    }

    /**
     * Forwards the thinking text carried by the event to the active chat
     * route response channel as a {@link ChatRouteConstants#THINKING} event.
     *
     * @param event the thinking event emitted by the AI service runtime
     */
    public static void forward(ThinkingEmitted event) {
        if (event == null) {
            return;
        }
        forward(event.text());
    }

    /**
     * Forwards an arbitrary message (typically a transformed or redacted
     * version of {@code event.text()}) to the active chat route response
     * channel.
     *
     * @param text the message to send; ignored when blank
     */
    public static void forward(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!ChatScope.isActive()) {
            return;
        }
        ChatRouteContext context;
        try {
            context = CDI.current().select(ChatRouteContext.class).get();
        } catch (Exception ignored) {
            return;
        }
        ChatRouteContext.ResponseChannel response = context.response();
        if (response != null) {
            response.thinking(text);
        }
    }
}

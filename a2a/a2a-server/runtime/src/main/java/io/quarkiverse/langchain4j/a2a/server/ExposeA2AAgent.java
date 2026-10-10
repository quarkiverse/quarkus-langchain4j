package io.quarkiverse.langchain4j.a2a.server;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.smallrye.common.annotation.Experimental;

/**
 * This annotation is meant to be placed on an AI Service (registered via {@link io.quarkiverse.langchain4j.RegisterAiService})
 * to expose it as an A2A server
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Experimental("The A2A story still has a lot of details to be fleshed out")
public @interface ExposeA2AAgent {

    /**
     * Advertises that the agent serves {@code SendStreamingMessage}, delivering task status and artifact updates
     * as they occur. The answer itself is not delivered in pieces: the exposed method returns once, so its result
     * reaches the client as a single artifact.
     *
     * @see <a href="https://a2a-protocol.org/latest/specification/#312-send-streaming-message">A2A spec §3.1.2 —
     *      Send Streaming Message</a>
     */
    boolean streaming() default false;

    /**
     * Advertises webhook delivery of task updates. The endpoints behind it are the A2A Java SDK's, whose behaviour
     * is known to diverge from the specification.
     *
     * @see <a href="https://a2a-protocol.org/latest/specification/#353-push-notification-delivery">A2A spec §3.5.3 —
     *      Push Notification Delivery</a>
     * @see <a href="https://github.com/a2aproject/a2a-java/issues/952">a2a-java#952</a>
     */
    boolean pushNotifications() default false;

    /**
     * Advertises that an authenticated, richer Agent Card is available. The card itself is the application's to
     * produce, as a bean qualified {@link org.a2aproject.sdk.server.ExtendedAgentCard}; enabling this without
     * producing one leaves clients being told the card exists and then refused it.
     */
    boolean extendedAgentCard() default false;

    Skill[] skills();

    @Retention(RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @interface Skill {

        String id();

        String name();

        String description();

        String[] tags() default {};

        String[] examples() default {};
    }
}

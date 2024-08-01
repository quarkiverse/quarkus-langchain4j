package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Provides a way for an AiService to get its chat memory seeded with examples interactions.
 * This is useful for creating few-shot prompts instead of hard coding the examples in the prompt itself.
 * <p>
 * The annotation is meant to be placed on a static method of an AiService, that returns {@code List<ChatMessage>}
 * and takes either no parameters or a single string parameter that represents the method of the AiService for which
 * the chat memory is being seeded.
 * <p>
 * The following code contains an example of how this can be used:
 *
 * <pre>
 *  {@code
 *
 * &#64;RegisterAiService
 * public interface TriageService {
 *
 *     &#64;SystemMessage("""
 *             Analyze the sentiment of the text below.
 *             Respond only with one word to describe the sentiment.
 *             """)
 *     Sentiment triage(String review);
 *
 *     enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }
 *
 *     &#64;SeedMemory
 *     static List<ChatMessage> seed() {
 *         return List.of(
 *                 UserMessage.from("I love you folks, you are the best!"), AiMessage.from(Sentiment.POSITIVE.name()),
 *                 UserMessage.from("J'adore votre banque"), AiMessage.from(Sentiment.POSITIVE.name()),
 *                 UserMessage.from("I hate this stuff, you are the worst!"), AiMessage.from(Sentiment.NEGATIVE.name()),
 *                 UserMessage.from("I really disliked the food. Who would overcook the steak like that?"), AiMessage.from(Sentiment.NEGATIVE.name()),
 *                 UserMessage.from("The Moon takes about one month to orbit Earth (27.3 days to complete a revolution, but 29.5 days to change from new moon to new moon)"), AiMessage.from(Sentiment.NEUTRAL.name()))
 *     }
 * }
 * }
 * </pre>
 * <p>
 * A few points must be made about the messages added via this method:
 * <ul>
 * <li>There must be an even number of messages that alternate between UserMessage and AiMessage</li>
 * <li>Implementations only get invoked if the chat memory for the specified memory id either doesn't exist or is empty</li>
 * <li>Whatever messages are created by the seed, do end up getting added to the chat memory (if it exists)</li>
 * </ul>
 */
@Retention(RUNTIME)
@Target({ METHOD })
public @interface SeedMemory {

}

package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.data.message.AiMessage;

/**
 * @deprecated These tests will go away once the Quarkus-specific guardrail implementation has been fully removed
 */
@Deprecated(forRemoval = true)
public class EmailEndsAppropriatelyOutputGuardrail implements OutputGuardrail {
    static final String REPROMPT_MESSAGE = "Invalid email";
    static final String EMAIL_ENDING = """

            Sincerely,
            Parasoft Insurance Claims Department

            --------------------------------------------
            Please note this is an unmonitored email box.
            Should you choose to reply, nobody (not even an AI bot) will see your message.
            Call a real human should you have any questions. 1-800-CAR-SAFE.""";

    static final String REPROMPT_PROMPT = """
            The response did not end properly. Please try again.

            The email body should end with the following text, EXACTLY as it appears below:
            """ + EMAIL_ENDING;

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        return responseFromLLM.text().endsWith(EMAIL_ENDING) ? success() : reprompt(REPROMPT_MESSAGE, REPROMPT_PROMPT);
    }
}

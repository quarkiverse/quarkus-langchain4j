package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.data.message.AiMessage;

public class EmailStartsAppropriatelyOutputGuardrail implements OutputGuardrail {
    static final String REPROMPT_MESSAGE = "Invalid email";
    static final String REPROMPT_PROMPT = """
            The response did not start with 'Dear'. Please try again.

            Only include the body of the email and make sure it starts appropriately.
            """;

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        return responseFromLLM.text().startsWith("Dear ") ? success() : reprompt(REPROMPT_MESSAGE, REPROMPT_PROMPT);
    }
}

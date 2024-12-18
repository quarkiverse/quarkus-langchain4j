package io.quarkiverse.langchain4j.guardrails;

import static io.quarkiverse.langchain4j.guardrails.GuardrailAssertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import io.quarkiverse.langchain4j.guardrails.GuardrailResult.Result;

class EmailStartsAppropriatelyOutputGuardrailTests {
    EmailStartsAppropriatelyOutputGuardrail guardrail = spy(new EmailStartsAppropriatelyOutputGuardrail());

    @Test
    void guardrailSuccess() {
        var aiMessage = AiMessage.from("Dear John,");

        assertThat(this.guardrail.validate(aiMessage))
                .isSuccessful();

        verify(this.guardrail).validate(aiMessage);
        verify(this.guardrail).success();
        verifyNoMoreInteractions(this.guardrail);
    }

    @Test
    void emailDoesntStartAppropriately() {
        var aiMessage = AiMessage.from("Hello there.");
        var guardrailResult = this.guardrail.validate(aiMessage);

        assertThat(guardrailResult)
                .hasFailures()
                .hasResult(Result.FATAL)
                .hasSingleFailureWithMessageAndReprompt(EmailStartsAppropriatelyOutputGuardrail.REPROMPT_MESSAGE,
                        EmailStartsAppropriatelyOutputGuardrail.REPROMPT_PROMPT);

        verify(this.guardrail).validate(aiMessage);
        verify(this.guardrail).reprompt(EmailStartsAppropriatelyOutputGuardrail.REPROMPT_MESSAGE,
                EmailStartsAppropriatelyOutputGuardrail.REPROMPT_PROMPT);
        verifyNoMoreInteractions(this.guardrail);
    }
}
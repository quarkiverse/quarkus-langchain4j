package io.quarkiverse.langchain4j.guardrails;

import static io.quarkiverse.langchain4j.guardrails.GuardrailAssertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.langchain4j.data.message.AiMessage;
import io.quarkiverse.langchain4j.guardrails.GuardrailResult.Result;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult.Failure;

/**
 * @deprecated These tests will go away once the Quarkus-specific guardrail implementation has been fully removed
 */
@Deprecated(forRemoval = true)
class EmailContainsRequiredInformationOutputGuardrailTests {
    private static final String CLAIM_NUMBER = "CLM195501";
    private static final String CLAIM_STATUS = "denied";
    private static final String CLIENT_NAME = "Marty McFly";
    private static final String EMAIL_TEMPLATE = """
            Dear %s,

            We are writing to inform you that your claim (%s) has been reviewed and is currently under consideration. After careful evaluation of the evidence provided, we regret to inform you that your claim has been %s.

            Please note that our decision is based on the information provided in your policy declarations page, as well as applicable laws and regulations governing vehicle insurance claims.

            If you have any questions or concerns regarding this decision, please do not hesitate to contact us at 800-CAR-SAFE or email claims@parasol.com. A member of our team will be happy to assist you.

            Sincerely,
            Parasoft Insurance Claims Department

            --------------------------------------------
            Please note this is an unmonitored email box.
            Should you choose to reply, nobody (not even an AI bot) will see your message.
            Call a real human should you have any questions. 1-800-CAR-SAFE.
            """;

    EmailContainsRequiredInformationOutputGuardrail guardrail = spy(new EmailContainsRequiredInformationOutputGuardrail());

    @Test
    void guardrailSuccess() {
        var response = EMAIL_TEMPLATE.formatted(CLIENT_NAME, CLAIM_NUMBER, CLAIM_STATUS);
        var params = createParams(response, CLAIM_NUMBER, CLAIM_STATUS, CLIENT_NAME);

        assertThat(this.guardrail.validate(params))
                .isSuccessful();

        verify(this.guardrail).validate(params);
        verify(this.guardrail).success();
        verifyNoMoreInteractions(this.guardrail);
    }

    @Test
    void emptyEmail() {
        var params = createParams("", CLAIM_NUMBER, CLAIM_STATUS, CLIENT_NAME);
        var result = this.guardrail.validate(params);

        assertThat(result)
                .hasFailures()
                .hasResult(Result.FATAL)
                .hasSingleFailureWithMessage(EmailContainsRequiredInformationOutputGuardrail.NO_RESPONSE_MESSAGE);

        verify(this.guardrail).validate(params);
        verify(this.guardrail).reprompt(EmailContainsRequiredInformationOutputGuardrail.NO_RESPONSE_MESSAGE,
                EmailContainsRequiredInformationOutputGuardrail.NO_RESPONSE_PROMPT);
        verifyNoMoreInteractions(this.guardrail);
    }

    @ParameterizedTest
    @MethodSource("emailDoesntContainRequiredInfoParams")
    void emailDoesntContainRequiredInfo(ClaimInfo missingClaimInfo, String expectedRepromptMessage,
            String expectedRepromptPrompt) {
        var responseWithMissingInfo = EMAIL_TEMPLATE.formatted(missingClaimInfo.clientName(), missingClaimInfo.claimNumber(),
                missingClaimInfo.claimStatus());
        var params = createParams(responseWithMissingInfo, CLAIM_NUMBER, CLAIM_STATUS, CLIENT_NAME);
        var result = this.guardrail.validate(params);

        assertThat(result)
                .hasFailures()
                .hasResult(Result.FATAL)
                .hasSingleFailureWithMessageAndReprompt(expectedRepromptMessage, expectedRepromptPrompt)
                .assertSingleFailureSatisfies(failure -> assertThat(failure)
                        .isNotNull()
                        .extracting(
                                Failure::retry,
                                Failure::message,
                                Failure::cause)
                        .containsExactly(
                                true,
                                expectedRepromptMessage,
                                null));

        verify(this.guardrail).validate(params);
        verify(this.guardrail).reprompt(expectedRepromptMessage, expectedRepromptPrompt);
        verifyNoMoreInteractions(this.guardrail);
    }

    static Stream<Arguments> emailDoesntContainRequiredInfoParams() {
        return Stream.of(
                Arguments.of(
                        new ClaimInfo("", CLAIM_NUMBER, CLAIM_STATUS),
                        EmailContainsRequiredInformationOutputGuardrail.CLIENT_NAME_NOT_FOUND_MESSAGE,
                        EmailContainsRequiredInformationOutputGuardrail.CLIENT_NAME_NOT_FOUND_PROMPT.formatted(CLIENT_NAME)),
                Arguments.of(
                        new ClaimInfo(CLIENT_NAME, "", CLAIM_STATUS),
                        EmailContainsRequiredInformationOutputGuardrail.CLAIM_NUMBER_NOT_FOUND_MESSAGE,
                        EmailContainsRequiredInformationOutputGuardrail.CLAIM_NUMBER_NOT_FOUND_PROMPT.formatted(CLAIM_NUMBER)),
                Arguments.of(
                        new ClaimInfo(CLIENT_NAME, CLAIM_NUMBER, ""),
                        EmailContainsRequiredInformationOutputGuardrail.CLAIM_STATUS_NOT_FOUND_MESSAGE,
                        EmailContainsRequiredInformationOutputGuardrail.CLAIM_STATUS_NOT_FOUND_PROMPT.formatted(CLAIM_STATUS)));
    }

    private static OutputGuardrailParams createParams(String response, String claimNumber, String claimStatus,
            String clientName) {
        return createParams(response, new ClaimInfo(clientName, claimNumber, claimStatus));
    }

    private static OutputGuardrailParams createParams(String response, ClaimInfo claimInfo) {
        return OutputGuardrailParams.from(AiMessage.from(response), Map.of("claimInfo", claimInfo));
    }
}
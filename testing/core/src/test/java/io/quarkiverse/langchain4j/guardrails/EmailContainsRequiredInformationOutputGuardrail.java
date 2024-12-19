package io.quarkiverse.langchain4j.guardrails;

import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;

public class EmailContainsRequiredInformationOutputGuardrail implements OutputGuardrail {
    static final String NO_RESPONSE_MESSAGE = "No response found";
    static final String NO_RESPONSE_PROMPT = "The response was empty. Please try again.";
    static final String CLIENT_NAME_NOT_FOUND_MESSAGE = "Client name not found";
    static final String CLIENT_NAME_NOT_FOUND_PROMPT = "The response did not contain the client name. Please include the client name \"%s\", exactly as is (case-sensitive), in the email body.";
    static final String CLAIM_NUMBER_NOT_FOUND_MESSAGE = "Claim number not found";
    static final String CLAIM_NUMBER_NOT_FOUND_PROMPT = "The response did not contain the claim number. Please include the claim number \"%s\", exactly as is (case-sensitive), in the email body.";
    static final String CLAIM_STATUS_NOT_FOUND_MESSAGE = "Claim status not found";
    static final String CLAIM_STATUS_NOT_FOUND_PROMPT = "The response did not contain the claim status. Please include the claim status \"%s\", exactly as is (case-sensitive), in the email body.";

    @Override
    public OutputGuardrailResult validate(OutputGuardrailParams params) {
        var claimInfo = Optional.ofNullable(params.variables())
                .map(vars -> vars.get("claimInfo"))
                .map(ClaimInfo.class::cast)
                .orElse(null);

        if (claimInfo != null) {
            var response = Optional.ofNullable(params.responseFromLLM())
                    .map(AiMessage::text)
                    .orElse("");

            if (response.isBlank()) {
                return reprompt(NO_RESPONSE_MESSAGE, NO_RESPONSE_PROMPT);
            }

            if (!claimInfo.clientName().isBlank() && !response.contains(claimInfo.clientName())) {
                return reprompt(CLIENT_NAME_NOT_FOUND_MESSAGE, CLIENT_NAME_NOT_FOUND_PROMPT.formatted(claimInfo.clientName()));
            }

            if (!claimInfo.claimNumber().isBlank() && !response.contains(claimInfo.claimNumber())) {
                return reprompt(CLAIM_NUMBER_NOT_FOUND_MESSAGE,
                        CLAIM_NUMBER_NOT_FOUND_PROMPT.formatted(claimInfo.claimNumber()));
            }

            if (!claimInfo.claimStatus().isBlank() && !response.contains(claimInfo.claimStatus())) {
                return reprompt(CLAIM_STATUS_NOT_FOUND_MESSAGE,
                        CLAIM_STATUS_NOT_FOUND_PROMPT.formatted(claimInfo.claimStatus()));
            }
        }

        return success();
    }
}

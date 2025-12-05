package io.quarkiverse.langchain4j.sample.guardrails.chat;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ProfanityGuardrail implements OutputGuardrail {
    private static final Logger LOG = Logger.getLogger(ProfanityGuardrail.class);

    private static final List<String> PROFANITY_LIST = List.of(
            "meatbag", "organics"
    );

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest params) {
        String response = params.responseFromLLM().aiMessage().text();
        if (containsProfanity(response)) {
            return failure("Response contains inappropriate content");
        }
        return success();
    }

    private boolean containsProfanity(String text) {
        LOG.info("Checking " + text);
        String lowerText = text.toLowerCase();
        return PROFANITY_LIST.stream().anyMatch(lowerText::contains);
    }
}

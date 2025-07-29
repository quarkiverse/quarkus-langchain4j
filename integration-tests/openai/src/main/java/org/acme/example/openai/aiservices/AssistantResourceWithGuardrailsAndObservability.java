package org.acme.example.openai.aiservices;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("assistant-with-guardrails-observability")
public class AssistantResourceWithGuardrailsAndObservability {
    private final Assistant assistant;

    public AssistantResourceWithGuardrailsAndObservability(Assistant assistant) {
        this.assistant = assistant;
    }

    @GET
    public String assistant() {
        return assistant.chat("test");
    }

    @RegisterAiService
    interface Assistant {
        @InputGuardrails({ IGDirectlyImplementInputGuardrailWithParams.class,
                IGDirectlyImplementInputGuardrailWithUserMessage.class, IGExtendingValidateWithParams.class,
                IGExtendingValidateWithUserMessage.class })
        @OutputGuardrails({ OGDirectlyImplementOutputGuardrailWithParams.class,
                OGDirectlyImplementOutputGuardrailWithAiMessage.class, OGExtendingValidateWithParams.class,
                OGExtendingValidateWithAiMessage.class })
        String chat(String message);
    }

    @ApplicationScoped
    public static class IGDirectlyImplementInputGuardrailWithUserMessage implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    @ApplicationScoped
    public static class IGDirectlyImplementInputGuardrailWithParams implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(InputGuardrailRequest request) {
            return success();
        }
    }

    @ApplicationScoped
    public static class OGDirectlyImplementOutputGuardrailWithParams implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest request) {
            return success();
        }
    }

    @ApplicationScoped
    public static class OGDirectlyImplementOutputGuardrailWithAiMessage implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    @ApplicationScoped
    public static class OGExtendingValidateWithParams extends AbstractOGImplementingValidateWithParams {
    }

    @ApplicationScoped
    public static class OGExtendingValidateWithAiMessage extends AbstractOGImplementingValidateWithAiMessage {
    }

    @ApplicationScoped
    public static class IGExtendingValidateWithParams extends AbstractIGImplementingValidateWithParams {
    }

    @ApplicationScoped
    public static class IGExtendingValidateWithUserMessage extends AbstractIGImplementingValidateWithUserMessage {
    }

    public static abstract class AbstractOGImplementingValidateWithParams implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest request) {
            return success();
        }
    }

    public static abstract class AbstractOGImplementingValidateWithAiMessage implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    public static abstract class AbstractIGImplementingValidateWithParams implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(InputGuardrailRequest request) {
            return success();
        }
    }

    public static abstract class AbstractIGImplementingValidateWithUserMessage implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }
}

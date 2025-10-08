package io.quarkiverse.langchain4j.audit.internal;

import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.ResponseFromLLMReceivedEvent;

/**
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public record DefaultResponseFromLLMReceivedEvent(AuditSourceInfo sourceInfo,
        ChatResponse response) implements ResponseFromLLMReceivedEvent {
}
package io.quarkiverse.langchain4j.audit.internal;

import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.ResponseFromLLMReceivedEvent;

public record DefaultResponseFromLLMReceivedEvent(AuditSourceInfo sourceInfo,
        ChatResponse response) implements ResponseFromLLMReceivedEvent {
}
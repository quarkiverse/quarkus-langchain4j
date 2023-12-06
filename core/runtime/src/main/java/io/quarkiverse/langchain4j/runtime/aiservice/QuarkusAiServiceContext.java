package io.quarkiverse.langchain4j.runtime.aiservice;

import dev.langchain4j.service.AiServiceContext;
import io.quarkiverse.langchain4j.audit.AuditService;

public class QuarkusAiServiceContext extends AiServiceContext {

    public AuditService auditService;

    public QuarkusAiServiceContext(Class<?> aiServiceClass) {
        super(aiServiceClass);
    }
}

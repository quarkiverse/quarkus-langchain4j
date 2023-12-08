package io.quarkiverse.langchain4j.deployment;

import org.jboss.jandex.DotName;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.UserName;
import io.quarkiverse.langchain4j.CreatedAware;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.audit.AuditService;

public class Langchain4jDotNames {
    public static final DotName CHAT_MODEL = DotName.createSimple(ChatLanguageModel.class);
    public static final DotName STREAMING_CHAT_MODEL = DotName.createSimple(StreamingChatLanguageModel.class);
    public static final DotName EMBEDDING_MODEL = DotName.createSimple(EmbeddingModel.class);
    public static final DotName MODERATION_MODEL = DotName.createSimple(ModerationModel.class);
    static final DotName AI_SERVICES = DotName.createSimple(AiServices.class);
    static final DotName CREATED_AWARE = DotName.createSimple(CreatedAware.class);
    static final DotName SYSTEM_MESSAGE = DotName.createSimple(SystemMessage.class);
    static final DotName USER_MESSAGE = DotName.createSimple(UserMessage.class);
    static final DotName USER_NAME = DotName.createSimple(UserName.class);
    static final DotName MODERATE = DotName.createSimple(Moderate.class);
    static final DotName MEMORY_ID = DotName.createSimple(MemoryId.class);
    static final DotName DESCRIPTION = DotName.createSimple(Description.class);
    static final DotName STRUCTURED_PROMPT = DotName.createSimple(StructuredPrompt.class);
    static final DotName STRUCTURED_PROMPT_PROCESSOR = DotName.createSimple(StructuredPromptProcessor.class);
    static final DotName REGISTER_AI_SERVICES = DotName.createSimple(RegisterAiService.class);

    static final DotName BEAN_CHAT_MODEL_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanChatLanguageModelSupplier.class);

    static final DotName CHAT_MEMORY_PROVIDER = DotName.createSimple(ChatMemoryProvider.class);

    static final DotName BEAN_CHAT_MEMORY_PROVIDER_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanChatMemoryProviderSupplier.class);

    static final DotName RETRIEVER = DotName.createSimple(Retriever.class);
    static final DotName TEXT_SEGMENT = DotName.createSimple(TextSegment.class);

    static final DotName AUDIT_SERVICE = DotName.createSimple(AuditService.class);

    static final DotName BEAN_RETRIEVER_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanRetrieverSupplier.class);

    static final DotName BEAN_IF_EXISTS_RETRIEVER_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanIfExistsRetrieverSupplier.class);

    static final DotName BEAN_IF_EXISTS_AUDIT_SERVICE_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanIfExistsAuditServiceSupplier.class);

}

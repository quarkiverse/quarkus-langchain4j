package io.quarkiverse.langchain4j.deployment;

import org.jboss.jandex.DotName;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.UserName;
import dev.langchain4j.service.V;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchTool;
import io.quarkiverse.langchain4j.CreatedAware;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.audit.AuditService;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContextQualifier;
import io.smallrye.mutiny.Multi;

public class LangChain4jDotNames {
    public static final DotName CHAT_MODEL = DotName.createSimple(ChatLanguageModel.class);
    public static final DotName STREAMING_CHAT_MODEL = DotName.createSimple(StreamingChatLanguageModel.class);
    public static final DotName EMBEDDING_MODEL = DotName.createSimple(EmbeddingModel.class);
    public static final DotName MODERATION_MODEL = DotName.createSimple(ModerationModel.class);
    public static final DotName IMAGE_MODEL = DotName.createSimple(ImageModel.class);
    static final DotName AI_SERVICES = DotName.createSimple(AiServices.class);
    static final DotName CREATED_AWARE = DotName.createSimple(CreatedAware.class);
    public static final DotName SYSTEM_MESSAGE = DotName.createSimple(SystemMessage.class);
    static final DotName USER_MESSAGE = DotName.createSimple(UserMessage.class);
    static final DotName USER_NAME = DotName.createSimple(UserName.class);
    static final DotName MODERATE = DotName.createSimple(Moderate.class);
    static final DotName MEMORY_ID = DotName.createSimple(MemoryId.class);
    static final DotName DESCRIPTION = DotName.createSimple(Description.class);
    static final DotName STRUCTURED_PROMPT = DotName.createSimple(StructuredPrompt.class);
    static final DotName STRUCTURED_PROMPT_PROCESSOR = DotName.createSimple(StructuredPromptProcessor.class);
    static final DotName V = DotName.createSimple(dev.langchain4j.service.V.class);

    static final DotName MODEL_NAME = DotName.createSimple(ModelName.class);
    static final DotName REGISTER_AI_SERVICES = DotName.createSimple(RegisterAiService.class);

    static final DotName BEAN_CHAT_MODEL_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanChatLanguageModelSupplier.class);

    static final DotName CHAT_MEMORY_PROVIDER = DotName.createSimple(ChatMemoryProvider.class);

    static final DotName BEAN_CHAT_MEMORY_PROVIDER_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanChatMemoryProviderSupplier.class);

    static final DotName NO_CHAT_MEMORY_PROVIDER_SUPPLIER = DotName.createSimple(
            RegisterAiService.NoChatMemoryProviderSupplier.class);

    static final DotName RETRIEVER = DotName.createSimple(Retriever.class);
    static final DotName NO_RETRIEVER = DotName.createSimple(
            RegisterAiService.NoRetriever.class);

    static final DotName RETRIEVAL_AUGMENTOR = DotName.createSimple(RetrievalAugmentor.class);
    static final DotName BEAN_IF_EXISTS_RETRIEVAL_AUGMENTOR_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanIfExistsRetrievalAugmentorSupplier.class);

    static final DotName NO_RETRIEVAL_AUGMENTOR_SUPPLIER = DotName.createSimple(
            RegisterAiService.NoRetrievalAugmentorSupplier.class);

    static final DotName AUDIT_SERVICE = DotName.createSimple(AuditService.class);

    static final DotName BEAN_IF_EXISTS_AUDIT_SERVICE_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanIfExistsAuditServiceSupplier.class);

    static final DotName BEAN_IF_EXISTS_MODERATION_MODEL_SUPPLIER = DotName.createSimple(
            RegisterAiService.BeanIfExistsModerationModelSupplier.class);

    static final DotName QUARKUS_AI_SERVICE_CONTEXT_QUALIFIER = DotName.createSimple(
            QuarkusAiServiceContextQualifier.class);

    static final DotName MULTI = DotName.createSimple(Multi.class);
    static final DotName STRING = DotName.createSimple(String.class);

    static final DotName WEB_SEARCH_TOOL = DotName.createSimple(WebSearchTool.class);
    static final DotName WEB_SEARCH_ENGINE = DotName.createSimple(WebSearchEngine.class);
}

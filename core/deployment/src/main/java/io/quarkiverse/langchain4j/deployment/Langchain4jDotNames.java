package io.quarkiverse.langchain4j.deployment;

import org.jboss.jandex.DotName;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.UserName;
import io.quarkiverse.langchain4j.CreatedAware;

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
}

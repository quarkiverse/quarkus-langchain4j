package io.quarkiverse.langchain4j.deployment;

import org.jboss.jandex.DotName;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.moderation.ModerationModel;

public class Langchain4jDotNames {
    public static final DotName CHAT_MODEL = DotName.createSimple(ChatLanguageModel.class);
    public static final DotName STREAMING_CHAT_MODEL = DotName.createSimple(StreamingChatLanguageModel.class);
    public static final DotName EMBEDDING_MODEL = DotName.createSimple(EmbeddingModel.class);
    public static final DotName MODERATION_MODEL = DotName.createSimple(ModerationModel.class);
}

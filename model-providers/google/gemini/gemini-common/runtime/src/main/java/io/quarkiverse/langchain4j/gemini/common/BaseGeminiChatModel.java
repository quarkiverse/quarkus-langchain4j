package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;

public class BaseGeminiChatModel {

    protected static final Logger log = Logger.getLogger(GeminiChatLanguageModel.class);
    protected final String modelId;
    protected final Double temperature;
    protected final Integer maxOutputTokens;
    protected final Integer topK;
    protected final Double topP;
    protected final ResponseFormat responseFormat;
    protected final List<ChatModelListener> listeners;

    public BaseGeminiChatModel(String modelId, Double temperature, Integer maxOutputTokens, Integer topK, Double topP,
            ResponseFormat responseFormat, List<ChatModelListener> listeners) {
        this.modelId = modelId;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.topK = topK;
        this.topP = topP;
        this.responseFormat = responseFormat;
        this.listeners = listeners;
    }
}

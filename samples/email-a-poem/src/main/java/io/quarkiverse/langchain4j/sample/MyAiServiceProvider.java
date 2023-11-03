package io.quarkiverse.langchain4j.sample;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

@ApplicationScoped
public class MyAiServiceProvider {

    private final MyAiService ai;

    public MyAiServiceProvider(ChatLanguageModel chatLanguageModel, EmailService email, LogService log) {
        this.ai = AiServices
                .builder(MyAiService.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(email, log)
                .build();

    }

    @Produces
    public MyAiService getAi() {
        return ai;
    }

}

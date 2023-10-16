package io.quarkiverse.langchain4j.it.chat;

import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import io.smallrye.mutiny.Multi;

@Path("chat")
public class ChatLanguageModelResource {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;

    public ChatLanguageModelResource(ChatLanguageModel chatLanguageModel,
            StreamingChatLanguageModel streamingChatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
    }

    @GET
    @Path("blocking")
    public String blocking() {
        return chatLanguageModel.generate("When was the nobel prize for economics first awarded?");
    }

    @GET
    @Path("streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streaming() {
        return Multi.createFrom().emitter(
                emitter -> {
                    streamingChatLanguageModel.generate(
                            "Write a short 1 paragraph funny poem about Java Applets",
                            new StreamingResponseHandler<>() {
                                @Override
                                public void onNext(String token) {
                                    emitter.emit(token);
                                }

                                @Override
                                public void onError(Throwable error) {
                                    emitter.fail(error);
                                }

                                @Override
                                public void onComplete(Response<AiMessage> response) {
                                    emitter.complete();
                                }
                            });
                });
    }

    @GET
    @Path("template")
    public String template() {
        String template = "Create a recipe for a {{dishType}} with the following ingredients: {{ingredients}}";
        PromptTemplate promptTemplate = PromptTemplate.from(template);

        Map<String, Object> variables = new HashMap<>();
        variables.put("dishType", "oven dish");
        variables.put("ingredients", "potato, tomato, feta, olive oil");

        Prompt prompt = promptTemplate.apply(variables);

        return chatLanguageModel.generate(prompt.text());
    }
}

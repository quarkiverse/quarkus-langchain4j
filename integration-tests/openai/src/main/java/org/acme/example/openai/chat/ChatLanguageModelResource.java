package org.acme.example.openai.chat;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import io.smallrye.mutiny.Multi;

@Path("chat")
public class ChatLanguageModelResource {

    private final ChatModel chatLanguageModel;
    private final StreamingChatModel streamingChatLanguageModel;

    public ChatLanguageModelResource(ChatModel chatLanguageModel,
            StreamingChatModel streamingChatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
    }

    @GET
    @Path("blocking")
    public String blocking() {
        return chatLanguageModel.chat("When was the nobel prize for economics first awarded?");
    }

    @GET
    @Path("streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streaming() {
        return Multi.createFrom().emitter(
                emitter -> {
                    streamingChatLanguageModel.chat(
                            "Write a short 1 paragraph funny poem about Java Applets",
                            new StreamingChatResponseHandler() {
                                @Override
                                public void onPartialResponse(String token) {
                                    emitter.emit(token);
                                }

                                @Override
                                public void onError(Throwable error) {
                                    emitter.fail(error);
                                }

                                @Override
                                public void onCompleteResponse(ChatResponse completeResponse) {
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

        return chatLanguageModel.chat(prompt.text());
    }

    @GET
    @Path("memory")
    public String memory() throws Exception {

        TokenCountEstimator tokenizer = new OpenAiTokenCountEstimator("gpt-3.5-turbo");
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(1000, tokenizer);

        StringBuffer sb = new StringBuffer();

        UserMessage userMessage1 = userMessage(
                "How do I optimize database queries for a large-scale e-commerce platform? "
                        + "Answer short in three to five lines maximum.");
        chatMemory.add(userMessage1);

        sb.append("[User]: ")
                .append(chatMessageToText(userMessage1))
                .append("\n[LLM]: ");

        AtomicReference<CompletableFuture<AiMessage>> futureRef = new AtomicReference<>(new CompletableFuture<>());

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String token) {
                sb.append(token);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                if (completeResponse != null) {
                    futureRef.get().complete(completeResponse.aiMessage());
                } else {
                    futureRef.get().complete(null);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                futureRef.get().completeExceptionally(throwable);
            }
        };

        streamingChatLanguageModel.chat(chatMemory.messages(), handler);
        AiMessage firstAiMessage = futureRef.get().get(60, TimeUnit.SECONDS);
        chatMemory.add(firstAiMessage);

        UserMessage userMessage2 = userMessage(
                "Give a concrete example implementation of the first point? " +
                        "Be short, 10 lines of code maximum.");
        chatMemory.add(userMessage2);

        sb.append("\n\n[User]: ")
                .append(chatMessageToText(userMessage2))
                .append("\n[LLM]: ");

        futureRef.set(new CompletableFuture<>());
        streamingChatLanguageModel.chat(chatMemory.messages(), handler);
        futureRef.get().get(60, TimeUnit.SECONDS);
        return sb.toString();
    }

    @GET
    @Path("structuredPrompt")
    public String structuredPrompt() {
        CreateRecipePrompt createRecipePrompt = new CreateRecipePrompt(
                "salad",
                List.of("cucumber", "tomato", "feta", "onion", "olives"));

        Prompt prompt = StructuredPromptProcessor.toPrompt(createRecipePrompt);

        return chatLanguageModel.chat(prompt.text());
    }

    @StructuredPrompt("""
            Create a recipe of a {{dish}} that can be prepared using only {{ingredients}}.
            Structure your answer in the following way:

            Recipe name: ...
            Description: ...
            Preparation time: ...

            Required ingredients:
            - ...
            - ...

            Instructions:
            - ...
            - ..."
            """)
    static class CreateRecipePrompt {

        String dish;
        List<String> ingredients;

        CreateRecipePrompt(String dish, List<String> ingredients) {
            this.dish = dish;
            this.ingredients = ingredients;
        }
    }

}

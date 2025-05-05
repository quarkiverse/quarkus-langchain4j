package org.acme.example.openai.chat.huggingface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;

@Path("chat")
public class ChatLanguageModelResource {

    private final ChatModel chatModel;

    public ChatLanguageModelResource(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GET
    @Path("basic")
    public String basic() {
        return chatModel.chat("When was the nobel prize for economics first awarded?");
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

        return chatModel.chat(prompt.text());
    }

    @GET
    @Path("structuredPrompt")
    public String structuredPrompt() {
        CreateRecipePrompt createRecipePrompt = new CreateRecipePrompt(
                "salad",
                List.of("cucumber", "tomato", "feta", "onion", "olives"));

        Prompt prompt = StructuredPromptProcessor.toPrompt(createRecipePrompt);

        return chatModel.chat(prompt.text());
    }

    @StructuredPrompt({
            "Create a recipe of a {{dish}} that can be prepared using only {{ingredients}}.",
            "Structure your answer in the following way:",

            "Recipe name: ...",
            "Description: ...",
            "Preparation time: ...",

            "Required ingredients:",
            "- ...",
            "- ...",

            "Instructions:",
            "- ...",
            "- ..."
    })
    static class CreateRecipePrompt {

        String dish;
        List<String> ingredients;

        CreateRecipePrompt(String dish, List<String> ingredients) {
            this.dish = dish;
            this.ingredients = ingredients;
        }
    }
}

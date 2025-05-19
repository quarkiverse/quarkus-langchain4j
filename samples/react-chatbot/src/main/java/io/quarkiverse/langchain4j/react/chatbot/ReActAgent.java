package io.quarkiverse.langchain4j.react.chatbot;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface ReActAgent {

    public record ReactResponse(String thought, String action, Map<String, Object> args, @JsonProperty("final_answer") String finalAnswer) {};

    @SystemMessage("""
        You are a helpful agent.
        Your task is to answer the user's questions step by step, using only the available tools, in the exact json format below:
        
        {
            "thought": <your reasoning about what to do next, as a string>,
            "action": <the name of the tool to use, as a string>,
            "args": <a JSON object containing the inputs for the action>
        }

        If you have all the information needed to answer the user's question, respond with the following:
        
        {
            "final_answer": <final answer as a string>
        }

        Additional instructions:
        - If you are not completely sure about the answer, you must use one or more tools to find accurate information.
        - Any important information must be validated using at least one different source/tool before it is used in the final_answer.
        - Only use the tools listed.
        - All parts of your reasoning must be in English.
        - Use as action only the tools listed.


        Tools:
        {tools}""")
    @UserMessage("""
        User question: {question}
        Tool execution result: {toolResult}""")
    public ReactResponse answer(@MemoryId String memoryId, String question, String toolResult);
}

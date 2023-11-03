package io.quarkiverse.langchain4j.sample;

import jakarta.enterprise.context.Dependent;

import dev.langchain4j.agent.tool.Tool;

@Dependent
public class LogService {

    @Tool("Log the given message")
    public void log(String message) {
        System.out.println(">> " + message);
    }

}

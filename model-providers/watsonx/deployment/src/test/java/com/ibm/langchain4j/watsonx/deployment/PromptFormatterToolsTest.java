package com.ibm.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.MistralLargePromptFormatter;
import io.quarkus.test.QuarkusUnitTest;

public class PromptFormatterToolsTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    List<ToolSpecification> tools = List.of(

            ToolSpecification.builder()
                    .name("sum")
                    .description("Perform a subtraction between two numbers")
                    .parameters(
                            ToolParameters.builder()
                                    .properties(Map.of("firstNumber", Map.of("type", "integer"), "secondNumber",
                                            Map.of("type", "integer")))
                                    .required(List.of("firstNumber", "secondNumber"))
                                    .type("object")
                                    .build())
                    .build());

    ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
            .id("1")
            .name("sum")
            .arguments("{\"firstNumber\":2,\"secondNumber\":2}\"}")
            .build();

    ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
            "1",
            "sum",
            "4");

    List<ChatMessage> messages = List.of(
            SystemMessage.from("You are a calculator"),
            UserMessage.from("2 + 2"),
            AiMessage.from(toolExecutionRequest),
            toolExecutionResultMessage,
            AiMessage.from("The result is 4"));

    @Test
    void mistral_large_tools_test() {
        MistralLargePromptFormatter promptFormatter = new MistralLargePromptFormatter();

        String expected_1 = """
                <s>[INST] You are a calculator [/INST]</s>[AVAILABLE_TOOLS] \
                [{"type":"function","function":{"name":"sum","description":"Perform a subtraction between two numbers","parameters":{"type":"object","properties":{"firstNumber":{"type":"integer"},"secondNumber":{"type":"integer"}},"required":["firstNumber","secondNumber"]}}}] \
                [/AVAILABLE_TOOLS][INST] 2 + 2 [/INST]\
                [TOOL_CALLS] [{"id":"1","name":"sum","arguments":{"firstNumber":2,"secondNumber":2}}]</s>\
                [TOOL_RESULTS] {"content":4,"id":"1"} [/TOOL_RESULTS] The result is 4</s>""";

        String expected_2 = """
                <s>[INST] You are a calculator [/INST]</s>[AVAILABLE_TOOLS] \
                [{"type":"function","function":{"name":"sum","description":"Perform a subtraction between two numbers","parameters":{"type":"object","properties":{"secondNumber":{"type":"integer"},"firstNumber":{"type":"integer"}},"required":["firstNumber","secondNumber"]}}}] \
                [/AVAILABLE_TOOLS][INST] 2 + 2 [/INST]\
                [TOOL_CALLS] [{"id":"1","name":"sum","arguments":{"firstNumber":2,"secondNumber":2}}]</s>\
                [TOOL_RESULTS] {"content":4,"id":"1"} [/TOOL_RESULTS] The result is 4</s>""";

        boolean result = false;

        if (expected_1.equals(promptFormatter.format(messages, tools)))
            result = true;
        else if (expected_2.equals(promptFormatter.format(messages, tools)))
            result = true;

        assertTrue(result);
    }
}

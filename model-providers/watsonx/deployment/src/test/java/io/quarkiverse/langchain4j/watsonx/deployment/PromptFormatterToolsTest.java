package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.fail;

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
import io.quarkiverse.langchain4j.watsonx.prompt.impl.Llama31PromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.MistralLargePromptFormatter;
import io.quarkus.test.QuarkusUnitTest;

public class PromptFormatterToolsTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    List<ToolSpecification> tools = List.of(

            ToolSpecification.builder()
                    .name("sum")
                    .description("Perform a sum between two numbers")
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

    List<ChatMessage> messagesWithSystem = List.of(
            SystemMessage.from("You are a calculator"),
            UserMessage.from("2 + 2"),
            AiMessage.from(toolExecutionRequest),
            toolExecutionResultMessage,
            AiMessage.from("The result is 4"));

    List<ChatMessage> messagesWithoutSystem = List.of(
            UserMessage.from("2 + 2"),
            AiMessage.from(toolExecutionRequest),
            toolExecutionResultMessage,
            AiMessage.from("The result is 4"));

    @Test
    void mistral_large_tools_test() {
        MistralLargePromptFormatter promptFormatter = new MistralLargePromptFormatter();

        String expected = """
                <s>%s[AVAILABLE_TOOLS] \
                [{"type":"function","function":{"name":"sum","description":"Perform a sum between two numbers","parameters":{"type":"object","properties":{%s},"required":["firstNumber","secondNumber"]}}}] \
                [/AVAILABLE_TOOLS][INST] 2 + 2 [/INST]\
                [TOOL_CALLS] [{"id":"1","name":"sum","arguments":{"firstNumber":2,"secondNumber":2}}]</s>\
                [TOOL_RESULTS] {"content":4,"id":"1"} [/TOOL_RESULTS] The result is 4</s>""";

        boolean isOk = false;
        String result = promptFormatter.format(messagesWithSystem, tools);

        if (expected
                .formatted("[INST] You are a calculator [/INST]</s>",
                        "\"firstNumber\":{\"type\":\"integer\"},\"secondNumber\":{\"type\":\"integer\"}")
                .equals(result))
            isOk = true;
        else if (expected
                .formatted("[INST] You are a calculator [/INST]</s>",
                        "\"secondNumber\":{\"type\":\"integer\"},\"firstNumber\":{\"type\":\"integer\"}")
                .equals(result))
            isOk = true;

        if (!isOk) {
            fail("The result %s is not what was expected (with SystemMessage)".formatted(result));
        }

        isOk = false;
        result = promptFormatter.format(messagesWithoutSystem, tools);

        if (expected.formatted("", "\"firstNumber\":{\"type\":\"integer\"},\"secondNumber\":{\"type\":\"integer\"}")
                .equals(result))
            isOk = true;
        else if (expected.formatted("", "\"secondNumber\":{\"type\":\"integer\"},\"firstNumber\":{\"type\":\"integer\"}")
                .equals(result))
            isOk = true;

        if (!isOk) {
            fail("The result %s is not what was expected (without SystemMessage)".formatted(result));
        }
    }

    @Test
    void llama_tools_test() {
        Llama31PromptFormatter promptFormatter = new Llama31PromptFormatter();

        String expected = """
                <|begin_of_text|><|start_header_id|>system<|end_header_id|>

                Environment: ipython
                Cutting Knowledge Date: December 2023
                Today Date: 26 Jul 2024

                You have access to the following functions. To call a function, respond with JSON for a function call. When you access a function respond always in the format {"name": function name, "parameters": dictionary of argument name and its value}. Do not use variables.

                {"type":"function","function":{"name":"sum","description":"Perform a sum between two numbers","parameters":{"type":"object","properties":{%s},"required":["firstNumber","secondNumber"]}}}

                %s<|eot_id|><|start_header_id|>user<|end_header_id|>

                2 + 2<|eot_id|><|start_header_id|>assistant<|end_header_id|>

                <|python_tag|>{"name":"sum","parameters":{"firstNumber":2,"secondNumber":2}}<|eom_id|><|start_header_id|>ipython<|end_header_id|>

                {"output":4}<|eot_id|><|start_header_id|>assistant<|end_header_id|>

                The result is 4<|eot_id|>""";

        boolean isOk = false;
        String result = promptFormatter.format(messagesWithSystem, tools);

        if (expected
                .formatted("\"firstNumber\":{\"type\":\"integer\"},\"secondNumber\":{\"type\":\"integer\"}",
                        "You are a calculator")
                .equals(result))
            isOk = true;
        else if (expected
                .formatted("\"secondNumber\":{\"type\":\"integer\"},\"firstNumber\":{\"type\":\"integer\"}",
                        "You are a calculator")
                .equals(result))
            isOk = true;

        if (!isOk) {
            fail("The result %s is not what was expected (with SystemMessage)".formatted(result));
        }

        isOk = false;
        result = promptFormatter.format(messagesWithoutSystem, tools);

        if (expected.formatted("\"firstNumber\":{\"type\":\"integer\"},\"secondNumber\":{\"type\":\"integer\"}", "")
                .equals(result))
            isOk = true;
        else if (expected.formatted("\"secondNumber\":{\"type\":\"integer\"},\"firstNumber\":{\"type\":\"integer\"}", "")
                .equals(result))
            isOk = true;

        if (!isOk) {
            fail("The result %s is not what was expected (without SystemMessage)".formatted(result));
        }
    }
}

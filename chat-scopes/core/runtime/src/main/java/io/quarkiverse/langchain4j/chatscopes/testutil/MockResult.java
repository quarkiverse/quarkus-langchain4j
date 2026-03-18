package io.quarkiverse.langchain4j.chatscopes.testutil;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;

public class MockResult<T> extends Result<T> {
    private List<ToolExecution> executions = new ArrayList<>();

    public MockResult(T content) {
        super(content, null, null, FinishReason.OTHER, null);
    }

    @Override
    public List<ToolExecution> toolExecutions() {
        return executions;
    }

    public void addExecution(ToolExecution execution) {
        executions.add(execution);
    }

    public void addToolResult(Object resultObject) {
        ToolExecutionRequest request = ToolExecutionRequest.builder().build();
        ToolExecutionResult result = ToolExecutionResult.builder().result(resultObject)
                .resultText(resultObject != null ? resultObject.toString() : "Success")
                .build();
        ToolExecution execution = ToolExecution.builder().invocationContext(InvocationContext.builder().build())
                .request(request).result(result).build();

        executions.add(execution);
    }

}

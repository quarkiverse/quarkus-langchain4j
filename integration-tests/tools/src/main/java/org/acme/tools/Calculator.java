package org.acme.tools;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;

@ApplicationScoped
public class Calculator {

    @Tool
    @Blocking
    public int blockingSum(int a, int b) {
        return a + b;
    }

    @Tool
    @NonBlocking
    public int nonBlockingSum(int a, int b) {
        return a + b;
    }

    @Tool
    @RunOnVirtualThread
    public int virtualSum(int a, int b) {
        return a + b;
    }
}

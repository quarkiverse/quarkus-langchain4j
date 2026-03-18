package io.quarkiverse.langchain4j.tests.scopes.invocation;

import jakarta.inject.Inject;

import io.quarkiverse.langchain4j.chatscopes.InvocationScoped;

@InvocationScoped
public class CalculateBean {

    @Inject
    CounterBean counterBean;

    public int add() {
        counterBean.increment();
        counterBean.increment();
        return counterBean.getCounter();
    }

}
